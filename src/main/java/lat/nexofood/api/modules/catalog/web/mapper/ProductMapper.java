package lat.nexofood.api.modules.catalog.web.mapper;

import lat.nexofood.api.modules.catalog.application.service.ProductPricingService;
import lat.nexofood.api.modules.catalog.domain.Category;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductPriceRequest;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.ProductPriceResponse;
import lat.nexofood.api.modules.catalog.web.dto.response.ProductResponse;
import lat.nexofood.api.modules.tenant.domain.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductMapper {

    private final ProductPriceMapper productPriceMapper;
    private final ProductPricingService pricingService;

    public ProductResponse toResponse(Product product) {
        if (product == null) {
            return null;
        }

        List<ProductPriceResponse> prices = product.getPrices() != null
                ? product.getPrices().stream().map(productPriceMapper::toResponse).toList()
                : Collections.emptyList();

        return ProductResponse.builder()
                .id(product.getId())
                .tenantId(product.getTenant() != null ? product.getTenant().getId() : null)
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .name(product.getName())
                .description(product.getDescription())
                .price(pricingService.calculateCurrentPrice(product))
                .basePrice(pricingService.getBasePrice(product))
                .prices(prices)
                .imageUrl(product.getImageUrl())
                .isAvailable(product.getIsAvailable())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public Product toEntity(ProductRequest request, Tenant tenant, Category category) {
        if (request == null) {
            return null;
        }

        Product product = Product.builder()
                .tenant(tenant)
                .category(category)
                .name(request.name())
                .description(request.description())
                .imageUrl(request.imageUrl())
                .isAvailable(request.isAvailable() != null ? request.isAvailable() : true)
                .prices(new ArrayList<>())
                .build();

        if (request.price() != null) {
            ProductPrice basePrice = ProductPrice.builder()
                    .product(product)
                    .name("Precio Base")
                    .price(request.price())
                    .isBase(true)
                    .priority(0)
                    .isActive(true)
                    .build();
            product.getPrices().add(basePrice);
        }

        if (request.prices() != null) {
            for (ProductPriceRequest priceRequest : request.prices()) {
                ProductPrice priceEntity = productPriceMapper.toEntity(priceRequest, product);
                product.getPrices().add(priceEntity);
            }
        }

        return product;
    }
}
