package lat.nexofood.api.modules.order.web.dto.response;

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
        String tenantSlug,
        String tenantName,
        UUID customerId,
        UUID customerUserId,
        String customerFullName,
        String customerEmail,
        String customerPhone,
        UUID deliveryStaffId,
        String deliveryStaffName,
        String orderNumber,
        String ruc,
        DeliveryType deliveryType,
        OrderStatus status,
        String deliveryAddress,
        Double deliveryLatitude,
        Double deliveryLongitude,
        BigDecimal estimatedDistanceKm,
        Integer estimatedDeliveryMinutes,
        OffsetDateTime estimatedArrivalAt,
        String etaMessage,
        OffsetDateTime dispatchedAt,
        OffsetDateTime deliveredAt,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal deliveryFee,
        BigDecimal total,
        String notes,
        List<OrderItemResponse> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
