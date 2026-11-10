package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.catalog.domain.Category;
import lat.nexofood.api.modules.catalog.infrastructure.repository.CategoryRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.catalog.web.dto.request.CategoryRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.CategoryResponse;
import lat.nexofood.api.modules.catalog.web.mapper.CategoryMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final TenantRepository tenantRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAllByTenant(UUID tenantId) {
        return categoryRepository.findAllByTenantIdOrderBySortOrderAsc(tenantId)
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> findActiveByTenant(UUID tenantId) {
        return categoryRepository.findAllByTenantIdAndIsActiveTrueOrderBySortOrderAsc(tenantId)
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse findByIdAndTenant(UUID tenantId, UUID categoryId) {
        Category category = getCategoryOrThrow(tenantId, categoryId);
        return categoryMapper.toResponse(category);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "storefront_menu", allEntries = true),
            @CacheEvict(value = "storefront_categories", allEntries = true)
    })
    @Transactional
    public CategoryResponse create(UUID tenantId, CategoryRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado con ID: " + tenantId));

        String normalizedName = request.name().trim();
        if (categoryRepository.existsByTenantIdAndNameIgnoreCase(tenantId, normalizedName)) {
            throw new ResourceConflictException("Ya existe una categoría con el nombre '" + normalizedName + "' en el restaurante.");
        }

        Category category = categoryMapper.toEntity(request, tenant);
        category.setName(normalizedName);
        Category saved = categoryRepository.save(category);
        return categoryMapper.toResponse(saved);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "storefront_menu", allEntries = true),
            @CacheEvict(value = "storefront_categories", allEntries = true)
    })
    @Transactional
    public CategoryResponse update(UUID tenantId, UUID categoryId, CategoryRequest request) {
        Category category = getCategoryOrThrow(tenantId, categoryId);

        String normalizedName = request.name().trim();
        if (categoryRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, normalizedName, categoryId)) {
            throw new ResourceConflictException("Ya existe otra categoría con el nombre '" + normalizedName + "' en el restaurante.");
        }

        category.setName(normalizedName);
        category.setDescription(request.description());
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.isActive() != null) {
            category.setIsActive(request.isActive());
        }

        Category updated = categoryRepository.save(category);
        return categoryMapper.toResponse(updated);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "storefront_menu", allEntries = true),
            @CacheEvict(value = "storefront_categories", allEntries = true)
    })
    @Transactional
    public CategoryResponse toggleStatus(UUID tenantId, UUID categoryId, boolean isActive) {
        Category category = getCategoryOrThrow(tenantId, categoryId);
        category.setIsActive(isActive);
        Category updated = categoryRepository.save(category);
        return categoryMapper.toResponse(updated);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "storefront_menu", allEntries = true),
            @CacheEvict(value = "storefront_categories", allEntries = true)
    })
    @Transactional
    public CategoryResponse reorder(UUID tenantId, UUID categoryId, int sortOrder) {
        if (sortOrder < 0) {
            throw new BadRequestException("El orden (sortOrder) debe ser mayor o igual a 0.");
        }
        Category category = getCategoryOrThrow(tenantId, categoryId);
        category.setSortOrder(sortOrder);
        Category updated = categoryRepository.save(category);
        return categoryMapper.toResponse(updated);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "storefront_menu", allEntries = true),
            @CacheEvict(value = "storefront_categories", allEntries = true)
    })
    @Transactional
    public void delete(UUID tenantId, UUID categoryId) {
        Category category = getCategoryOrThrow(tenantId, categoryId);

        if (productRepository.existsByCategoryId(categoryId)) {
            throw new BadRequestException(
                    "No se puede eliminar una categoría que tiene productos asociados. " +
                    "Reasigne los productos a otra categoría o desactívela en su lugar."
            );
        }

        categoryRepository.delete(category);
    }

    private Category getCategoryOrThrow(UUID tenantId, UUID categoryId) {
        return categoryRepository.findByIdAndTenantId(categoryId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + categoryId));
    }
}
