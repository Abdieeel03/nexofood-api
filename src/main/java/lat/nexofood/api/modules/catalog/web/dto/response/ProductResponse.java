package lat.nexofood.api.modules.catalog.web.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record ProductResponse(
        UUID id,
        UUID tenantId,
        UUID categoryId,
        String categoryName,
        UUID taxId,
        String taxName,
        BigDecimal taxRate,
        Boolean taxInclusive,
        String name,
        String description,
        BigDecimal price,
        BigDecimal basePrice,
        List<ProductPriceResponse> prices,
        String imageUrl,
        Boolean isAvailable,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
