package lat.nexofood.api.modules.order.dto.response;

import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record OrderResponse(
        UUID id,
        UUID tenantId,
        UUID customerId,
        String customerFullName,
        String customerPhone,
        UUID deliveryStaffId,
        String orderNumber,
        DeliveryType deliveryType,
        OrderStatus status,
        String deliveryAddress,
        Double deliveryLatitude,
        Double deliveryLongitude,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal total,
        String notes,
        List<OrderItemResponse> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
