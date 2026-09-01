package lat.nexofood.api.modules.order.mapper;

import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderItem;
import lat.nexofood.api.modules.order.dto.request.OrderItemRequest;
import lat.nexofood.api.modules.order.dto.response.OrderItemResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class OrderItemMapper {

    public OrderItemResponse toResponse(OrderItem item) {
        if (item == null) {
            return null;
        }
        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .productName(item.getProductName())
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .subtotal(item.getSubtotal())
                .notes(item.getNotes())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    public OrderItem toEntity(OrderItemRequest request, Product product, Order order) {
        if (request == null || product == null) {
            return null;
        }
        BigDecimal unitPrice = product.getPrice();
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));

        return OrderItem.builder()
                .order(order)
                .product(product)
                .productName(product.getName())
                .unitPrice(unitPrice)
                .quantity(request.quantity())
                .subtotal(subtotal)
                .notes(request.notes())
                .build();
    }
}
