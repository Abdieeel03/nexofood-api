package lat.nexofood.api.modules.catalog.dto.response;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record CategoryResponse(
        UUID id,
        UUID tenantId,
        String name,
        String description,
        Integer sortOrder,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
