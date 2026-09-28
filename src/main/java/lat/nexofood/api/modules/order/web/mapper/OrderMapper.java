package lat.nexofood.api.modules.order.web.mapper;

import lat.nexofood.api.common.util.GeoUtils;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderItem;
import lat.nexofood.api.modules.order.web.dto.response.OrderItemResponse;
import lat.nexofood.api.modules.order.web.dto.response.OrderResponse;
import lat.nexofood.api.modules.store.domain.Tenant;
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

        if (order.getCustomer() != null) {
            customerId = order.getCustomer().getId();
            User customerUser = order.getCustomer().getUser();
            if (customerUser != null) {
                customerUserId = customerUser.getId();
                customerFullName = customerUser.getFullName();
                customerEmail = customerUser.getEmail();
                customerPhone = customerUser.getPhone();
            }
        }

        return OrderResponse.builder()
                .id(order.getId())
                .tenantId(order.getTenant() != null ? order.getTenant().getId() : null)
                .customerId(customerId)
                .customerUserId(customerUserId)
                .customerFullName(customerFullName)
                .customerEmail(customerEmail)
                .customerPhone(customerPhone)
                .deliveryStaffId(order.getDeliveryStaff() != null ? order.getDeliveryStaff().getId() : null)
                .orderNumber(order.getOrderNumber())
                .deliveryType(order.getDeliveryType())
                .status(order.getStatus())
                .deliveryAddress(order.getDeliveryAddress())
                .deliveryLatitude(GeoUtils.getLatitude(order.getDeliveryLocation()))
                .deliveryLongitude(GeoUtils.getLongitude(order.getDeliveryLocation()))
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .total(order.getTotal())
                .notes(order.getNotes())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
