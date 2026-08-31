package lat.nexofood.api.modules.catalog.mapper;

import lat.nexofood.api.modules.catalog.domain.Category;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.dto.request.ProductRequest;
import lat.nexofood.api.modules.catalog.dto.response.ProductResponse;
import lat.nexofood.api.modules.tenant.domain.Tenant;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        if (product == null) {
            return null;
        }
        return ProductResponse.builder()
                .id(product.getId())
                .tenantId(product.getTenant() != null ? product.getTenant().getId() : null)
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
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
        return Product.builder()
                .tenant(tenant)
                .category(category)
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .imageUrl(request.imageUrl())
                .isAvailable(request.isAvailable() != null ? request.isAvailable() : true)
                .build();
    }
}
