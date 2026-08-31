package lat.nexofood.api.modules.catalog.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record ProductResponse(
        UUID id,
        UUID tenantId,
        UUID categoryId,
        String categoryName,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        Boolean isAvailable,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
