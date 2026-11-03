package lat.nexofood.api.modules.store.application.service.storefront;

import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.catalog.application.service.CalculateProductPriceService;
import lat.nexofood.api.modules.catalog.application.service.GetBasePriceService;
import lat.nexofood.api.modules.catalog.application.service.GetEffectivePriceRuleService;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingService;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingServiceImpl;
import lat.nexofood.api.modules.catalog.application.service.TaxCalculationService;
import lat.nexofood.api.modules.catalog.domain.Category;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lat.nexofood.api.modules.catalog.domain.Tax;
import lat.nexofood.api.modules.catalog.infrastructure.repository.CategoryRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.catalog.web.dto.response.CategoryResponse;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.response.StorefrontMenuResponse;
import lat.nexofood.api.modules.store.web.dto.response.StorefrontProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorefrontMenuServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductRepository productRepository;

    private StorefrontMenuServiceImpl storefrontMenuService;
    private Tenant tenant;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        GetBasePriceService basePriceService = new GetBasePriceService();
        GetEffectivePriceRuleService effectiveRuleService = new GetEffectivePriceRuleService(basePriceService);
        CalculateProductPriceService calculatePriceService = new CalculateProductPriceService(effectiveRuleService);
        ProductPricingService pricingService = new ProductPricingServiceImpl(
                calculatePriceService, basePriceService, effectiveRuleService
        );
        TaxCalculationService taxCalculationService = new TaxCalculationService();

        storefrontMenuService = new StorefrontMenuServiceImpl(
                tenantRepository,
                categoryRepository,
                productRepository,
                pricingService,
                taxCalculationService
        );

        tenantId = UUID.randomUUID();
        tenant = Tenant.builder()
                .id(tenantId)
                .name("Burger Bros")
                .slug("burger-bros")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("getMenuBySlug: retorna categorías activas con productos disponibles, precios vigentes y desglose tributario")
    void getMenuBySlugReturnsActiveCategoriesAndProducts() {
        Category burgersCat = Category.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name("Hamburguesas")
                .sortOrder(1)
                .isActive(true)
                .build();

        Tax igv = Tax.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name("IGV 18%")
                .rate(new BigDecimal("18.00"))
                .isInclusive(true)
                .isActive(true)
                .build();

        ProductPrice baseRule = ProductPrice.builder()
                .name("Precio Base")
                .price(new BigDecimal("118.00"))
                .isBase(true)
                .priority(0)
                .isActive(true)
                .build();

        Product burger = Product.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .category(burgersCat)
                .tax(igv)
                .name("Burger Suprema")
                .isAvailable(true)
                .prices(List.of(baseRule))
                .build();

        when(tenantRepository.findBySlug("burger-bros")).thenReturn(Optional.of(tenant));
        when(categoryRepository.findAllByTenantIdAndIsActiveTrueOrderBySortOrderAsc(tenantId))
                .thenReturn(List.of(burgersCat));
        when(productRepository.findAllByTenantIdAndIsAvailableTrue(tenantId))
                .thenReturn(List.of(burger));

        StorefrontMenuResponse menu = storefrontMenuService.getMenuBySlug("burger-bros");

        assertEquals("Burger Bros", menu.tenantName());
        assertEquals(1, menu.categories().size());
        assertEquals("Hamburguesas", menu.categories().getFirst().name());
        assertEquals(1, menu.categories().getFirst().products().size());

        StorefrontProductResponse item = menu.categories().getFirst().products().getFirst();
        assertEquals("Burger Suprema", item.name());
        assertEquals(new BigDecimal("118.00"), item.price());
        assertEquals(new BigDecimal("100.00"), item.baseUnitPrice());
        assertEquals(new BigDecimal("18.00"), item.taxAmount());
        assertEquals(new BigDecimal("118.00"), item.finalPrice());
    }

    @Test
    @DisplayName("getActiveCategoriesBySlug y getProductsBySlug: listan categorías activas y productos paginados para el cliente")
    void getCategoriesAndPaginatedProductsBySlug() {
        UUID categoryId = UUID.randomUUID();
        Category activeCat = Category.builder()
                .id(categoryId)
                .tenant(tenant)
                .name("Bebidas")
                .sortOrder(2)
                .isActive(true)
                .build();

        Product soda = Product.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .category(activeCat)
                .name("Limonada Frozen")
                .isAvailable(true)
                .prices(List.of(
                        ProductPrice.builder().name("Base").price(new BigDecimal("12.00")).isBase(true).isActive(true).build()
                ))
                .build();

        Pageable pageable = PageRequest.of(0, 10);

        when(tenantRepository.findBySlug("burger-bros")).thenReturn(Optional.of(tenant));
        when(categoryRepository.findAllByTenantIdAndIsActiveTrueOrderBySortOrderAsc(tenantId))
                .thenReturn(List.of(activeCat));
        when(categoryRepository.findByIdAndTenantId(categoryId, tenantId))
                .thenReturn(Optional.of(activeCat));
        when(productRepository.findAllByTenantIdAndCategoryIdAndIsAvailableTrue(tenantId, categoryId, pageable))
                .thenReturn(new PageImpl<>(List.of(soda), pageable, 1));

        List<CategoryResponse> categories = storefrontMenuService.getActiveCategoriesBySlug("burger-bros");
        assertEquals(1, categories.size());
        assertEquals("Bebidas", categories.getFirst().name());

        PageResponse<StorefrontProductResponse> productPage = storefrontMenuService.getProductsBySlug(
                "burger-bros", categoryId, null, pageable
        );
        assertEquals(1, productPage.totalElements());
        assertEquals("Limonada Frozen", productPage.content().getFirst().name());
        assertEquals(new BigDecimal("12.00"), productPage.content().getFirst().finalPrice());
    }

    @Test
    @DisplayName("getProductDetailBySlug: lanza 404 si el producto no está disponible o pertenece a categoría inactiva")
    void getProductDetailRejectsUnavailableProduct() {
        UUID productId = UUID.randomUUID();
        Product unavailableProduct = Product.builder()
                .id(productId)
                .tenant(tenant)
                .name("Producto Agotado")
                .isAvailable(false)
                .build();

        when(tenantRepository.findBySlug("burger-bros")).thenReturn(Optional.of(tenant));
        when(productRepository.findByIdAndTenantId(productId, tenantId)).thenReturn(Optional.of(unavailableProduct));

        assertThrows(ResourceNotFoundException.class, () ->
                storefrontMenuService.getProductDetailBySlug("burger-bros", productId)
        );
    }
}
