package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ForbiddenException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.infrastructure.repository.OrderRepository;
import lat.nexofood.api.modules.order.web.dto.response.KitchenOrderResponse;
import lat.nexofood.api.modules.order.web.mapper.KitchenOrderMapper;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lat.nexofood.api.modules.payment.infrastructure.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KitchenOrderServiceImpl implements KitchenOrderService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final KitchenOrderMapper kitchenOrderMapper;

    @Override
    @Transactional(readOnly = true)
    public List<KitchenOrderResponse> getOrders(UUID tenantId, OrderStatus status) {
        List<Order> orders;
        if (status == null) {
            orders = orderRepository.findKitchenQueueOrders(
                    tenantId,
                    OrderStatus.PENDIENTE,
                    OrderStatus.EN_PREPARACION,
                    PaymentStatus.APPROVED
            );
        } else if (status == OrderStatus.PENDIENTE) {
            orders = orderRepository.findPaidOrdersByTenantIdAndStatusOrderByCreatedAtAsc(
                    tenantId,
                    OrderStatus.PENDIENTE,
                    PaymentStatus.APPROVED
            );
        } else if (status == OrderStatus.EN_PREPARACION) {
            orders = orderRepository.findAllByTenantIdAndStatusOrderByCreatedAtAsc(
                    tenantId,
                    OrderStatus.EN_PREPARACION
            );
        } else {
            throw new BadRequestException("El filtro de estado en cocina solo permite PENDIENTE o EN_PREPARACION");
        }

        return orders.stream()
                .map(kitchenOrderMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public KitchenOrderResponse getOrderById(UUID tenantId, UUID orderId) {
        Order order = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada con ID: " + orderId));

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == OrderStatus.PENDIENTE) {
            boolean approved = paymentRepository.findByOrderId(orderId)
                    .map(payment -> payment.getStatus() == PaymentStatus.APPROVED)
                    .orElse(false);
            if (!approved) {
                throw new ForbiddenException("Esta orden aún no tiene un pago aprobado visible para cocina");
            }
        } else if (currentStatus != OrderStatus.EN_PREPARACION
                && currentStatus != OrderStatus.LISTO_PARA_ENTREGA) {
            throw new ForbiddenException("Esta orden no se encuentra en un estado visible para cocina");
        }

        return kitchenOrderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public KitchenOrderResponse updateStatus(UUID tenantId, UUID orderId, OrderStatus status) {
        if (status != OrderStatus.EN_PREPARACION && status != OrderStatus.LISTO_PARA_ENTREGA) {
            throw new BadRequestException("En cocina solo se puede cambiar el estado a EN_PREPARACION o LISTO_PARA_ENTREGA");
        }

        orderService.updateStatus(tenantId, orderId, status);

        Order updatedOrder = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada con ID: " + orderId));
        return kitchenOrderMapper.toResponse(updatedOrder);
    }
}
