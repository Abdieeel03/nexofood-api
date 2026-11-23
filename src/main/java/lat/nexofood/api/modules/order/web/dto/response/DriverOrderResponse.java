package lat.nexofood.api.modules.order.web.dto.response;

import lat.nexofood.api.modules.order.domain.OrderStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record DriverOrderResponse(
        UUID id,
        String orderNumber,
        OrderStatus status,
        String customerFullName,
        String customerPhone,
        String deliveryAddress,
        Double deliveryLatitude,
        Double deliveryLongitude,
        String pickupStoreName,
        String pickupAddress,
        String pickupPhone,
        Double pickupLatitude,
        Double pickupLongitude,
        BigDecimal estimatedDistanceKm,
        Integer estimatedDeliveryMinutes,
        OffsetDateTime estimatedArrivalAt,
        String etaMessage,
        OffsetDateTime dispatchedAt,
        OffsetDateTime deliveredAt,
        String notes,
        List<DriverOrderItemResponse> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
