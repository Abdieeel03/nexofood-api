package lat.nexofood.api.modules.tracking.web.dto.response;

import lat.nexofood.api.modules.order.domain.OrderStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record DeliveryTrackingResponse(
        UUID orderId,
        String orderNumber,
        OrderStatus orderStatus,
        UUID tenantId,
        String tenantName,
        Double storeLatitude,
        Double storeLongitude,
        String deliveryAddress,
        Double destinationLatitude,
        Double destinationLongitude,
        UUID driverId,
        String driverName,
        String driverPhone,
        Double driverLatitude,
        Double driverLongitude,
        Double heading,
        Double speedKmh,
        Double accuracyM,
        BigDecimal remainingDistanceKm,
        Integer etaMinutes,
        Integer etaMinMinutes,
        Integer etaMaxMinutes,
        OffsetDateTime estimatedArrivalAt,
        String etaMessage,
        boolean trackingActive,
        boolean stale,
        boolean throttled,
        int pollIntervalSeconds,
        OffsetDateTime lastUpdatedAt,
        OffsetDateTime dispatchedAt,
        OffsetDateTime deliveredAt,
        List<TrackingPointResponse> routeHistory
) {}

