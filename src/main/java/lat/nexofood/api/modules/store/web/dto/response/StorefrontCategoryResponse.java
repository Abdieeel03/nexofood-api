package lat.nexofood.api.modules.store.web.dto.response;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record StorefrontCategoryResponse(
        UUID id,
        String name,
        String description,
        Integer sortOrder,
        List<StorefrontProductResponse> products
) {}
