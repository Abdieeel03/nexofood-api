package lat.nexofood.api.modules.tracking.application.service;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Builder
public record EtaEstimate(
        BigDecimal straightDistanceKm,
        BigDecimal estimatedRouteDistanceKm,
        int estimatedMinutes,
        int minMinutes,
        int maxMinutes,
        OffsetDateTime estimatedArrivalAt,
        String message
) {}

