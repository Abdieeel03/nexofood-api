package lat.nexofood.api.modules.catalog.mapper;

import lat.nexofood.api.modules.catalog.domain.Category;
import lat.nexofood.api.modules.catalog.dto.request.CategoryRequest;
import lat.nexofood.api.modules.catalog.dto.response.CategoryResponse;
import lat.nexofood.api.modules.tenant.domain.Tenant;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        if (category == null) {
            return null;
        }
        return CategoryResponse.builder()
                .id(category.getId())
                .tenantId(category.getTenant() != null ? category.getTenant().getId() : null)
                .name(category.getName())
                .description(category.getDescription())
                .sortOrder(category.getSortOrder())
                .isActive(category.getIsActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    public Category toEntity(CategoryRequest request, Tenant tenant) {
        if (request == null) {
            return null;
        }
        return Category.builder()
                .tenant(tenant)
                .name(request.name())
                .description(request.description())
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .isActive(request.isActive() != null ? request.isActive() : true)
                .build();
    }
}
