package lat.nexofood.api.modules.order.web.mapper;

import lat.nexofood.api.common.util.GeoUtils;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.web.dto.response.OrderItemResponse;
import lat.nexofood.api.modules.order.web.dto.response.OrderResponse;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final OrderItemMapper orderItemMapper;

    public OrderResponse toResponse(Order order) {
        if (order == null) {
            return null;
        }

        List<OrderItemResponse> itemResponses = order.getItems() != null
                ? order.getItems().stream().map(orderItemMapper::toResponse).toList()
                : Collections.emptyList();

        UUID customerId = null;
        UUID customerUserId = null;
        String customerFullName = null;
        String customerEmail = null;
        String customerPhone = null;

        TenantCustomer customer = order.getCustomer();
        if (customer != null) {
            User customerUser = customer.getUser();
            if (customerUser != null) {
                customerUserId = customerUser.getId();
                customerId = customerUserId;
                customerFullName = customerUser.getFullName();
                customerEmail = customerUser.getEmail();
                customerPhone = customerUser.getPhone();
            }
            if (customerId == null && customer.getId() != null) {
                customerId = customer.getId().getUserId();
                customerUserId = customerId;
            }
        }

        Tenant tenant = order.getTenant();
        User deliveryStaff = order.getDeliveryStaff();

        return OrderResponse.builder()
                .id(order.getId())
                .tenantId(tenant != null ? tenant.getId() : null)
                .tenantSlug(tenant != null ? tenant.getSlug() : null)
                .tenantName(tenant != null ? tenant.getName() : null)
                .customerId(customerId)
                .customerUserId(customerUserId)
                .customerFullName(customerFullName)
                .customerEmail(customerEmail)
                .customerPhone(customerPhone)
                .deliveryStaffId(deliveryStaff != null ? deliveryStaff.getId() : null)
                .deliveryStaffName(deliveryStaff != null ? deliveryStaff.getFullName() : null)
                .orderNumber(order.getOrderNumber())
                .ruc(order.getRuc())
                .deliveryType(order.getDeliveryType())
                .status(order.getStatus())
                .deliveryAddress(order.getDeliveryAddress())
                .deliveryLatitude(GeoUtils.getLatitude(order.getDeliveryLocation()))
                .deliveryLongitude(GeoUtils.getLongitude(order.getDeliveryLocation()))
                .estimatedDistanceKm(order.getEstimatedDistanceKm())
                .estimatedDeliveryMinutes(order.getEstimatedDeliveryMinutes())
                .estimatedArrivalAt(order.getEstimatedArrivalAt())
                .etaMessage(buildEtaMessage(order))
                .dispatchedAt(order.getDispatchedAt())
                .deliveredAt(order.getDeliveredAt())
                .subtotal(order.getSubtotal())
                .taxTotal(order.getTaxTotal())
                .deliveryFee(order.getDeliveryFee())
                .total(order.getTotal())
                .notes(order.getNotes())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private String buildEtaMessage(Order order) {
        if (order == null || order.getDeliveryType() != DeliveryType.DELIVERY) {
            return null;
        }
        if (order.getStatus() == OrderStatus.ENTREGADO) {
            return "Tu pedido ha sido entregado";
        }
        if (order.getStatus() == OrderStatus.CANCELADO) {
            return "Tu pedido fue cancelado";
        }
        if (order.getEstimatedDeliveryMinutes() != null && order.getEstimatedDeliveryMinutes() > 0) {
            return "Tu pedido estará en tu casa en ~" + order.getEstimatedDeliveryMinutes() + " min";
        }
        return null;
    }
}
