package lat.nexofood.api.modules.store.web.dto.response;

import lombok.Builder;

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
        List<StorefrontCategoryResponse> categories,
        List<StorefrontProductResponse> uncategorizedProducts
) {}
