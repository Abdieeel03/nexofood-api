package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ForbiddenException;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(
                OrderStatus.PENDIENTE,
                EnumSet.of(OrderStatus.EN_PREPARACION, OrderStatus.CANCELADO)
        );
        ALLOWED_TRANSITIONS.put(
                OrderStatus.EN_PREPARACION,
                EnumSet.of(OrderStatus.LISTO_PARA_ENTREGA, OrderStatus.CANCELADO)
        );
        ALLOWED_TRANSITIONS.put(
                OrderStatus.LISTO_PARA_ENTREGA,
                EnumSet.of(OrderStatus.EN_CAMINO, OrderStatus.ENTREGADO)
        );
        ALLOWED_TRANSITIONS.put(
                OrderStatus.EN_CAMINO,
                EnumSet.of(OrderStatus.ENTREGADO)
        );
        ALLOWED_TRANSITIONS.put(OrderStatus.ENTREGADO, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELADO, EnumSet.noneOf(OrderStatus.class));
    }

    public void validateTransition(
            Order order,
            OrderStatus targetStatus,
            TenantStaffRole staffRole,
            boolean isSuperAdmin,
            UUID actorUserId
    ) {
        if (order == null || targetStatus == null) {
            throw new BadRequestException("La orden y el estado destino son obligatorios");
        }

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == targetStatus) {
            throw new BadRequestException("El pedido ya se encuentra en el estado " + targetStatus);
        }

        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, EnumSet.noneOf(OrderStatus.class));
        if (!allowed.contains(targetStatus)) {
            throw new BadRequestException(String.format(
                    "Transición de estado no permitida: %s → %s",
                    currentStatus,
                    targetStatus
            ));
        }

        // En pedidos DELIVERY, para pasar de LISTO_PARA_ENTREGA se debe pasar primero por EN_CAMINO
        if (currentStatus == OrderStatus.LISTO_PARA_ENTREGA
                && targetStatus == OrderStatus.ENTREGADO
                && order.getDeliveryType() == DeliveryType.DELIVERY) {
            throw new BadRequestException(
                    "Un pedido con entrega a domicilio (DELIVERY) debe pasar primero a EN_CAMINO antes de marcarse como ENTREGADO"
            );
        }

        // En pedidos TAKEAWAY o DINE_IN no aplica el estado EN_CAMINO
        if (targetStatus == OrderStatus.EN_CAMINO
                && order.getDeliveryType() != null
                && order.getDeliveryType() != DeliveryType.DELIVERY) {
            throw new BadRequestException(
                    "El estado EN_CAMINO solo aplica para pedidos con entrega a domicilio (DELIVERY)"
            );
        }

        if (isSuperAdmin || staffRole == TenantStaffRole.OWNER || staffRole == TenantStaffRole.ADMIN) {
            return;
        }

        if (staffRole == null) {
            throw new ForbiddenException("No tienes permisos en este restaurante para cambiar el estado del pedido");
        }

        switch (staffRole) {
            case COCINERO -> {
                if (targetStatus != OrderStatus.EN_PREPARACION && targetStatus != OrderStatus.LISTO_PARA_ENTREGA) {
                    throw new ForbiddenException(
                            "El rol COCINERO solo puede cambiar el estado a EN_PREPARACION o LISTO_PARA_ENTREGA"
                    );
                }
            }
            case REPARTIDOR -> {
                if (targetStatus != OrderStatus.EN_CAMINO && targetStatus != OrderStatus.ENTREGADO) {
                    throw new ForbiddenException(
                            "El rol REPARTIDOR solo puede cambiar el estado a EN_CAMINO o ENTREGADO"
                    );
                }
                if (order.getDeliveryStaff() != null
                        && actorUserId != null
                        && !actorUserId.equals(order.getDeliveryStaff().getId())) {
                    throw new ForbiddenException(
                            "Este pedido está asignado a otro repartidor"
                    );
                }
            }
            default -> throw new ForbiddenException("Rol no autorizado para cambiar el estado del pedido");
        }
    }
}
