package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ForbiddenException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.infrastructure.repository.OrderRepository;
import lat.nexofood.api.modules.order.web.dto.response.DriverOrderResponse;
import lat.nexofood.api.modules.order.web.mapper.DriverOrderMapper;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverOrderServiceImpl implements DriverOrderService {

    private static final Set<OrderStatus> DRIVER_VISIBLE_STATUSES = EnumSet.of(
            OrderStatus.EN_PREPARACION,
            OrderStatus.LISTO_PARA_ENTREGA,
            OrderStatus.EN_CAMINO,
            OrderStatus.ENTREGADO
    );

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final DriverOrderMapper driverOrderMapper;
    private final CurrentUserProvider currentUserProvider;
    private final TenantAccessService tenantAccessService;

    @Override
    @Transactional(readOnly = true)
    public List<DriverOrderResponse> getOrders(UUID tenantId, OrderStatus status) {
        if (currentUserProvider == null) {
            throw new BadRequestException("No se pudo identificar al repartidor autenticado");
        }
        UUID staffUserId = currentUserProvider.getCurrentUserId();
        if (staffUserId == null) {
            throw new ForbiddenException("No se pudo identificar al repartidor autenticado");
        }

        List<Order> orders;
        if (status == null) {
            orders = orderRepository.findAllByTenantIdAndDeliveryStaffIdAndStatusInOrderByCreatedAtAsc(
                    tenantId,
                    staffUserId,
                    DRIVER_VISIBLE_STATUSES
            );
        } else if (DRIVER_VISIBLE_STATUSES.contains(status)) {
            orders = orderRepository.findAllByTenantIdAndDeliveryStaffIdAndStatusOrderByCreatedAtAsc(
                    tenantId,
                    staffUserId,
                    status
            );
        } else {
            throw new BadRequestException(
                    "El filtro de estado para repartidor solo permite EN_PREPARACION, LISTO_PARA_ENTREGA, EN_CAMINO o ENTREGADO"
            );
        }

        return orders.stream()
                .map(driverOrderMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DriverOrderResponse getOrderById(UUID tenantId, UUID orderId) {
        Order order = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada con ID: " + orderId));

        validateOrderReadPermission(tenantId, order);
        return driverOrderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public DriverOrderResponse updateStatus(UUID tenantId, UUID orderId, OrderStatus status) {
        if (status != OrderStatus.EN_CAMINO && status != OrderStatus.ENTREGADO) {
            throw new BadRequestException("El repartidor solo puede cambiar el estado a EN_CAMINO o ENTREGADO");
        }

        orderService.updateStatus(tenantId, orderId, status);

        Order updatedOrder = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada con ID: " + orderId));
        return driverOrderMapper.toResponse(updatedOrder);
    }

    private void validateOrderReadPermission(UUID tenantId, Order order) {
        if (tenantAccessService != null && tenantAccessService.isSuperAdmin()) {
            return;
        }
        TenantStaffRole role = tenantAccessService != null
                ? tenantAccessService.getCurrentUserRoleInTenant(tenantId).orElse(null)
                : null;
        if (role == TenantStaffRole.OWNER || role == TenantStaffRole.ADMIN) {
            return;
        }

        UUID actorUserId = currentUserProvider != null ? currentUserProvider.getCurrentUserId() : null;
        if (order.getDeliveryStaff() == null
                || actorUserId == null
                || !actorUserId.equals(order.getDeliveryStaff().getId())) {
            throw new ForbiddenException("Solo el repartidor asignado a este pedido puede consultarlo");
        }
    }
}
