package lat.nexofood.api.modules.order.mapper;

import lat.nexofood.api.common.util.GeoUtils;
import lat.nexofood.api.modules.auth.domain.User;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderItem;
import lat.nexofood.api.modules.order.dto.response.OrderItemResponse;
import lat.nexofood.api.modules.order.dto.response.OrderResponse;
import lat.nexofood.api.modules.tenant.domain.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

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

        return OrderResponse.builder()
                .id(order.getId())
                .tenantId(order.getTenant() != null ? order.getTenant().getId() : null)
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .customerFullName(order.getCustomer() != null ? order.getCustomer().getFullName() : null)
                .customerPhone(order.getCustomer() != null ? order.getCustomer().getPhone() : null)
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
