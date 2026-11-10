package lat.nexofood.api.modules.store.application.service.storefront;

import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.catalog.web.dto.response.CategoryResponse;
import lat.nexofood.api.modules.store.web.dto.response.StorefrontMenuResponse;
import lat.nexofood.api.modules.store.web.dto.response.StorefrontProductResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface StorefrontMenuService {

    StorefrontMenuResponse getMenuBySlug(String slug);

    List<CategoryResponse> getActiveCategoriesBySlug(String slug);

    PageResponse<StorefrontProductResponse> getProductsBySlug(
            String slug,
            UUID categoryId,
            String search,
            Pageable pageable
    );

    StorefrontProductResponse getProductDetailBySlug(String slug, UUID productId);
}
