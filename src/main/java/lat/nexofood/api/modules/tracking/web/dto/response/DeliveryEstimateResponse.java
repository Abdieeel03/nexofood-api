package lat.nexofood.api.modules.tracking.web.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record DeliveryEstimateResponse(
        UUID tenantId,
        String tenantSlug,
        String tenantName,
        boolean withinCoverage,
        BigDecimal maxDeliveryRadiusKm,
        BigDecimal straightDistanceKm,
        BigDecimal estimatedRouteDistanceKm,
        BigDecimal deliveryFee,
        Integer estimatedDeliveryMinutes,
        Integer minMinutes,
        Integer maxMinutes,
        OffsetDateTime estimatedArrivalAt,
        String message
) {}

