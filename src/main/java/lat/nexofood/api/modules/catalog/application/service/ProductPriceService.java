package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.modules.catalog.web.dto.request.ProductPriceRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.ProductPriceResponse;

import java.util.List;
import java.util.UUID;

public interface ProductPriceService {

    List<ProductPriceResponse> findAllByProduct(UUID tenantId, UUID productId);

    ProductPriceResponse findById(UUID tenantId, UUID productId, UUID priceId);

    ProductPriceResponse create(UUID tenantId, UUID productId, ProductPriceRequest request);

    ProductPriceResponse update(UUID tenantId, UUID productId, UUID priceId, ProductPriceRequest request);

    void delete(UUID tenantId, UUID productId, UUID priceId);

    void validatePriceRuleFields(ProductPriceRequest request);
}
