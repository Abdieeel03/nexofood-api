package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.modules.catalog.web.dto.request.ProductRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.ProductResponse;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    ProductResponse create(UUID tenantId, ProductRequest request);

    ProductResponse update(UUID tenantId, UUID productId, ProductRequest request);

    ProductResponse findByIdAndTenant(UUID tenantId, UUID productId);

    List<ProductResponse> findAllByTenant(UUID tenantId);
}
