package lat.nexofood.api.modules.store.web.dto.response;

import lombok.Builder;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Builder
public record StorefrontMenuResponse(
        UUID tenantId,
        String tenantName,
        String tenantSlug,
        String logoUrl,
        String bannerUrl,
        Boolean onlinePaymentsEnabled,
        LocalTime openingTime,
        LocalTime closingTime,
        Boolean isOpen,
        List<StorefrontCategoryResponse> categories,
        List<StorefrontProductResponse> uncategorizedProducts
) {}
