package lat.nexofood.api.modules.store.web.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record StorefrontProductResponse(
        UUID id,
        UUID tenantId,
        UUID categoryId,
        String categoryName,
        String name,
        String description,
        String imageUrl,
        BigDecimal price,
        BigDecimal basePrice,
        String activePromotionName,
        BigDecimal discountPercentage,
        UUID taxId,
        String taxName,
        BigDecimal taxRate,
        Boolean taxInclusive,
        BigDecimal baseUnitPrice,
        BigDecimal taxAmount,
        BigDecimal finalPrice,
        Boolean isAvailable
) {}
