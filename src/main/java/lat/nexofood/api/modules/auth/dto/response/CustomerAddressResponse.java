package lat.nexofood.api.modules.auth.dto.response;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record CustomerAddressResponse(
        UUID id,
        UUID userId,
        String title,
        String addressLine,
        String reference,
        Double latitude,
        Double longitude,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
