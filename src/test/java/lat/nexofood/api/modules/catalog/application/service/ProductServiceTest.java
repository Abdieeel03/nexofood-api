package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.catalog.domain.Category;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lat.nexofood.api.modules.catalog.domain.Tax;
import lat.nexofood.api.modules.catalog.infrastructure.repository.CategoryRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.TaxRepository;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductPriceRequest;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.ProductResponse;
import lat.nexofood.api.modules.catalog.web.mapper.ProductMapper;
import lat.nexofood.api.modules.catalog.web.mapper.ProductPriceMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.subscription.application.service.guard.SubscriptionGuard;
import lat.nexofood.api.modules.subscription.domain.SubscriptionPlan;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TaxRepository taxRepository;
    @Mock
    private SubscriptionGuard subscriptionGuard;

    private ProductServiceImpl productService;
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
        ProductMapper productMapper = new ProductMapper(new ProductPriceMapper(), pricingService);

        productService = new ProductServiceImpl(
                productRepository,
                tenantRepository,
                categoryRepository,
                taxRepository,
                productMapper,
                subscriptionGuard
        );

        tenantId = UUID.randomUUID();
        tenant = Tenant.builder().id(tenantId).name("Burger Bros").build();
    }

    @Test
    @DisplayName("create: respeta el límite maxProducts del plan de suscripción")
    void createProductEnforcesSubscriptionMaxProductsLimit() {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .id(UUID.randomUUID())
                .name("Plan Básico")
                .maxProducts(2)
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(subscriptionGuard.getLimits(tenantId)).thenReturn(plan);
        when(productRepository.countByTenantId(tenantId)).thenReturn(2L);

        ProductRequest request = ProductRequest.builder()
                .name("Hamburguesa Especial")
                .price(new BigDecimal("22.00"))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> productService.create(tenantId, request));
        assertTrue(ex.getMessage().contains("límite máximo de productos"));
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: crea el producto con precio base e impuesto activo")
    void createProductSuccessfully() {
        UUID categoryId = UUID.randomUUID();
        UUID taxId = UUID.randomUUID();

        SubscriptionPlan plan = SubscriptionPlan.builder().maxProducts(50).build();
        Category category = Category.builder().id(categoryId).tenant(tenant).name("Clásicas").build();
        Tax tax = Tax.builder().id(taxId).tenant(tenant).name("IGV 18%").rate(new BigDecimal("18.00")).isInclusive(true).isActive(true).build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(subscriptionGuard.getLimits(tenantId)).thenReturn(plan);
        when(productRepository.countByTenantId(tenantId)).thenReturn(5L);
        when(categoryRepository.findByIdAndTenantId(categoryId, tenantId)).thenReturn(Optional.of(category));
        when(taxRepository.findByIdAndTenantId(taxId, tenantId)).thenReturn(Optional.of(tax));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        ProductRequest request = ProductRequest.builder()
                .categoryId(categoryId)
                .taxId(taxId)
                .name("Royal Burger")
                .description("Con huevo y queso")
                .price(new BigDecimal("24.50"))
                .isAvailable(true)
                .build();

        ProductResponse response = productService.create(tenantId, request);

        assertNotNull(response.id());
        assertEquals("Royal Burger", response.name());
        assertEquals(new BigDecimal("24.50"), response.price());
        assertEquals(new BigDecimal("24.50"), response.basePrice());
        assertEquals("Clásicas", response.categoryName());
        assertEquals("IGV 18%", response.taxName());
    }

    @Test
    @DisplayName("create: rechaza múltiples precios base activos en la creación")
    void createProductRejectsMultipleBasePrices() {
        SubscriptionPlan plan = SubscriptionPlan.builder().maxProducts(50).build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(subscriptionGuard.getLimits(tenantId)).thenReturn(plan);
        when(productRepository.countByTenantId(tenantId)).thenReturn(0L);

        ProductRequest request = ProductRequest.builder()
                .name("Producto Inválido")
                .price(new BigDecimal("15.00"))
                .prices(List.of(
                        ProductPriceRequest.builder()
                                .name("Otro Base")
                                .price(new BigDecimal("18.00"))
                                .isBase(true)
                                .isActive(true)
                                .build()
                ))
                .build();

        assertThrows(ResourceConflictException.class, () -> productService.create(tenantId, request));
    }

    @Test
    @DisplayName("toggleAvailability y delete (soft): actualizan isAvailable correctamente")
    void toggleAvailabilityAndSoftDelete() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
                .id(productId)
                .tenant(tenant)
                .name("Papas Fritas")
                .isAvailable(true)
                .prices(new ArrayList<>(List.of(
                        ProductPrice.builder().name("Precio Base").price(new BigDecimal("9.00")).isBase(true).isActive(true).build()
                )))
                .build();

        when(productRepository.findByIdAndTenantId(productId, tenantId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse toggled = productService.toggleAvailability(tenantId, productId, false);
        assertFalse(toggled.isAvailable());

        productService.toggleAvailability(tenantId, productId, true);
        assertTrue(product.getIsAvailable());

        productService.delete(tenantId, productId);
        assertFalse(product.getIsAvailable());
    }

    @Test
    @DisplayName("findAllByTenant paginado: filtra por categoría y disponibilidad")
    void findPaginatedProductsWithFilters() {
        UUID categoryId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        Product product = Product.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name("Cheeseburger")
                .isAvailable(true)
                .prices(List.of(ProductPrice.builder().name("Base").price(new BigDecimal("19.00")).isBase(true).isActive(true).build()))
                .build();

        when(productRepository.findAllByTenantIdAndCategoryIdAndIsAvailableTrue(tenantId, categoryId, pageable))
                .thenReturn(new PageImpl<>(List.of(product), pageable, 1));

        PageResponse<ProductResponse> pageResponse = productService.findAllByTenant(tenantId, categoryId, true, pageable);

        assertEquals(1, pageResponse.totalElements());
        assertEquals("Cheeseburger", pageResponse.content().getFirst().name());
    }
}
