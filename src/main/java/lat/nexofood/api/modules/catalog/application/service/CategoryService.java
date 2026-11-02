package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.modules.catalog.web.dto.request.CategoryRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.CategoryResponse;

import java.util.List;
import java.util.UUID;

public interface CategoryService {

    List<CategoryResponse> findAllByTenant(UUID tenantId);

    List<CategoryResponse> findActiveByTenant(UUID tenantId);

    CategoryResponse findByIdAndTenant(UUID tenantId, UUID categoryId);

    CategoryResponse create(UUID tenantId, CategoryRequest request);

    CategoryResponse update(UUID tenantId, UUID categoryId, CategoryRequest request);

    CategoryResponse toggleStatus(UUID tenantId, UUID categoryId, boolean isActive);

    CategoryResponse reorder(UUID tenantId, UUID categoryId, int sortOrder);

    void delete(UUID tenantId, UUID categoryId);
}
