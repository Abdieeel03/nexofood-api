package lat.nexofood.api.modules.order.web.mapper;

import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderItem;
import lat.nexofood.api.modules.order.web.dto.response.KitchenOrderItemResponse;
import lat.nexofood.api.modules.order.web.dto.response.KitchenOrderResponse;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Mapper de órdenes para cocina (KDS) sin información financiera ni PII sensible del cliente.
 */
@Component
public class KitchenOrderMapper {

    public KitchenOrderResponse toResponse(Order order) {
        if (order == null) {
            return null;
        }

        List<KitchenOrderItemResponse> itemResponses = order.getItems() != null
                ? order.getItems().stream().map(this::toItemResponse).toList()
                : Collections.emptyList();

        return KitchenOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .deliveryType(order.getDeliveryType())
                .status(order.getStatus())
                .customerFirstName(extractFirstName(order.getCustomer()))
                .notes(order.getNotes())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    public KitchenOrderItemResponse toItemResponse(OrderItem item) {
        if (item == null) {
            return null;
        }
        return KitchenOrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .notes(item.getNotes())
                .build();
    }

    private String extractFirstName(TenantCustomer customer) {
        if (customer == null || customer.getUser() == null || customer.getUser().getFullName() == null) {
            return "Comensal";
        }
        String trimmed = customer.getUser().getFullName().trim();
        if (trimmed.isEmpty()) {
            return "Comensal";
        }
        return trimmed.split("\\s+")[0];
    }
}
