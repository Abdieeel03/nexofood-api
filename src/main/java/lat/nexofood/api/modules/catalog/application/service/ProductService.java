package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.ProductResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    ProductResponse create(UUID tenantId, ProductRequest request);

    ProductResponse update(UUID tenantId, UUID productId, ProductRequest request);

    ProductResponse findByIdAndTenant(UUID tenantId, UUID productId);

    List<ProductResponse> findAllByTenant(UUID tenantId);

    PageResponse<ProductResponse> findAllByTenant(
            UUID tenantId,
            UUID categoryId,
            Boolean availableOnly,
            Pageable pageable
    );

    ProductResponse toggleAvailability(UUID tenantId, UUID productId, boolean isAvailable);

    void delete(UUID tenantId, UUID productId);
}
