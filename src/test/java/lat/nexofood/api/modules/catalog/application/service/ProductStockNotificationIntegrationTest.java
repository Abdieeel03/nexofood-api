package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lat.nexofood.api.modules.catalog.infrastructure.repository.CategoryRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.TaxRepository;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductRequest;
import lat.nexofood.api.modules.catalog.web.mapper.ProductMapper;
import lat.nexofood.api.modules.catalog.web.mapper.ProductPriceMapper;
import lat.nexofood.api.modules.notification.application.service.NotificationService;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.subscription.application.service.guard.SubscriptionGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductStockNotificationIntegrationTest {

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
    @Mock
    private NotificationService notificationService;

    private ProductStockServiceImpl productStockService;
    private ProductServiceImpl productService;
    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        GetBasePriceService basePriceService = new GetBasePriceService();
        GetEffectivePriceRuleService effectiveRuleService = new GetEffectivePriceRuleService(basePriceService);
        CalculateProductPriceService calculatePriceService = new CalculateProductPriceService(effectiveRuleService);
        ProductPricingService pricingService = new ProductPricingServiceImpl(
                calculatePriceService, basePriceService, effectiveRuleService
        );
        ProductMapper productMapper = new ProductMapper(new ProductPriceMapper(), pricingService);

        productStockService = new ProductStockServiceImpl(productRepository, notificationService);
        productService = new ProductServiceImpl(
                productRepository,
                tenantRepository,
                categoryRepository,
                taxRepository,
                productMapper,
                subscriptionGuard,
                notificationService
        );

        tenantId = UUID.randomUUID();
        tenant = Tenant.builder().id(tenantId).name("Burger Bros").build();
    }

    @Test
    @DisplayName("consumeStock: notifica al NotificationService tras descontar stock")
    void consumeStockNotifiesLowOrZeroStock() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
                .id(productId)
                .tenant(tenant)
                .name("Hamburguesa Clásica")
                .stock(5)
                .isAvailable(true)
                .build();

        when(productRepository.findByIdAndTenantIdForUpdate(productId, tenantId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productStockService.consumeStock(tenantId, Map.of(productId, 3));

        assertEquals(2, product.getStock());
        verify(productRepository).save(product);
        verify(notificationService).checkAndNotifyProductStock(product);
    }

    @Test
    @DisplayName("consumeStock: funciona con constructor sin NotificationService (null-safe)")
    void consumeStockWorksWithoutNotificationService() {
        ProductStockServiceImpl stockServiceWithoutNotifications = new ProductStockServiceImpl(productRepository);
        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
                .id(productId)
                .tenant(tenant)
                .name("Gaseosa")
                .stock(4)
                .isAvailable(true)
                .build();

        when(productRepository.findByIdAndTenantIdForUpdate(productId, tenantId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        stockServiceWithoutNotifications.consumeStock(tenantId, Map.of(productId, 2));

        assertEquals(2, product.getStock());
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("ProductServiceImpl create, update y updateStock disparan verificación de stock en NotificationService")
    void productServiceLifecycleMethodsNotifyStockCheck() {
        UUID productId = UUID.randomUUID();
        Product existing = Product.builder()
                .id(productId)
                .tenant(tenant)
                .name("Pizza Pepperoni")
                .stock(10)
                .isAvailable(true)
                .prices(new ArrayList<>(List.of(
                        ProductPrice.builder()
                                .name("Precio Base")
                                .price(new BigDecimal("35.00"))
                                .isBase(true)
                                .isActive(true)
                                .build()
                )))
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(productRepository.findByIdAndTenantIdForUpdate(productId, tenantId)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest createReq = ProductRequest.builder()
                .name("Limonada")
                .price(new BigDecimal("8.00"))
                .stock(3)
                .build();
        productService.create(tenantId, createReq);

        ProductRequest updateReq = ProductRequest.builder()
                .name("Pizza Pepperoni Especial")
                .price(new BigDecimal("38.00"))
                .stock(2)
                .build();
        productService.update(tenantId, productId, updateReq);

        productService.updateStock(tenantId, productId, 0);

        verify(notificationService).checkAndNotifyProductStock(org.mockito.ArgumentMatchers.argThat(
                p -> "Limonada".equals(p.getName()) && Integer.valueOf(3).equals(p.getStock())
        ));
        verify(notificationService, org.mockito.Mockito.times(2)).checkAndNotifyProductStock(existing);
    }
}
