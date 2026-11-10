package lat.nexofood.api.modules.tracking.web.dto.response;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record TrackingPointResponse(
        Double latitude,
        Double longitude,
        Double heading,
        Double speedKmh,
        Double accuracyM,
        OffsetDateTime recordedAt
) {}

