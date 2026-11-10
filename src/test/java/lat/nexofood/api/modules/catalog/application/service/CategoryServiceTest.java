package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.modules.catalog.domain.Category;
import lat.nexofood.api.modules.catalog.infrastructure.repository.CategoryRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.catalog.web.dto.request.CategoryRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.CategoryResponse;
import lat.nexofood.api.modules.catalog.web.mapper.CategoryMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private ProductRepository productRepository;

    private CategoryServiceImpl categoryService;
    private Tenant tenant;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryServiceImpl(
                categoryRepository,
                tenantRepository,
                productRepository,
                new CategoryMapper()
        );
        tenantId = UUID.randomUUID();
        tenant = Tenant.builder().id(tenantId).name("Burger Bros").build();
    }

    @Test
    @DisplayName("create: crea una categoría cuando el nombre es único en el restaurante")
    void createCategorySuccessfully() {
        CategoryRequest request = CategoryRequest.builder()
                .name("Hamburguesas")
                .description("Clásicas y artesanales")
                .sortOrder(1)
                .isActive(true)
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(categoryRepository.existsByTenantIdAndNameIgnoreCase(tenantId, "Hamburguesas")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CategoryResponse response = categoryService.create(tenantId, request);

        assertNotNull(response.id());
        assertEquals("Hamburguesas", response.name());
        assertEquals(1, response.sortOrder());
        assertTrue(response.isActive());
    }

    @Test
    @DisplayName("create: rechaza nombre duplicado en el mismo restaurante con 409 Conflict")
    void createCategoryRejectsDuplicateName() {
        CategoryRequest request = CategoryRequest.builder().name("Bebidas").build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(categoryRepository.existsByTenantIdAndNameIgnoreCase(tenantId, "Bebidas")).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> categoryService.create(tenantId, request));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("reorder y toggleStatus: actualizan orden y estado de la categoría")
    void reorderAndToggleCategory() {
        UUID categoryId = UUID.randomUUID();
        Category category = Category.builder()
                .id(categoryId)
                .tenant(tenant)
                .name("Postres")
                .sortOrder(0)
                .isActive(true)
                .build();

        when(categoryRepository.findByIdAndTenantId(categoryId, tenantId)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse reordered = categoryService.reorder(tenantId, categoryId, 5);
        assertEquals(5, reordered.sortOrder());

        CategoryResponse toggled = categoryService.toggleStatus(tenantId, categoryId, false);
        assertFalse(toggled.isActive());
    }

    @Test
    @DisplayName("delete: bloquea la eliminación si la categoría tiene productos asociados")
    void deleteCategoryBlockedWhenHasProducts() {
        UUID categoryId = UUID.randomUUID();
        Category category = Category.builder().id(categoryId).tenant(tenant).name("Entradas").build();

        when(categoryRepository.findByIdAndTenantId(categoryId, tenantId)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryId(categoryId)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> categoryService.delete(tenantId, categoryId));
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: elimina la categoría cuando no tiene productos asociados")
    void deleteCategorySuccessfullyWhenEmpty() {
        UUID categoryId = UUID.randomUUID();
        Category category = Category.builder().id(categoryId).tenant(tenant).name("Entradas").build();

        when(categoryRepository.findByIdAndTenantId(categoryId, tenantId)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryId(categoryId)).thenReturn(false);

        categoryService.delete(tenantId, categoryId);
        verify(categoryRepository).delete(category);
    }
}
