package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingService;
import lat.nexofood.api.modules.catalog.application.service.ProductService;
import lat.nexofood.api.modules.catalog.application.service.ProductServiceImpl;
import lat.nexofood.api.modules.catalog.application.service.TaxCalculationService;
import lat.nexofood.api.modules.catalog.application.service.TaxService;
import lat.nexofood.api.modules.catalog.application.service.TaxServiceImpl;
import lat.nexofood.api.modules.catalog.domain.Category;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.Tax;
import lat.nexofood.api.modules.catalog.infrastructure.repository.CategoryRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.TaxRepository;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductRequest;
import lat.nexofood.api.modules.catalog.web.dto.request.TaxRequest;
import lat.nexofood.api.modules.catalog.web.mapper.ProductMapper;
import lat.nexofood.api.modules.catalog.web.mapper.ProductPriceMapper;
import lat.nexofood.api.modules.catalog.web.mapper.TaxMapper;
import lat.nexofood.api.modules.identity.infrastructure.repository.CustomerAddressRepository;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderItem;
import lat.nexofood.api.modules.order.infrastructure.repository.OrderItemRepository;
import lat.nexofood.api.modules.order.infrastructure.repository.OrderRepository;
import lat.nexofood.api.modules.order.web.dto.request.OrderCreateRequest;
import lat.nexofood.api.modules.order.web.dto.request.OrderItemRequest;
import lat.nexofood.api.modules.order.web.dto.response.OrderItemResponse;
import lat.nexofood.api.modules.order.web.dto.response.OrderResponse;
import lat.nexofood.api.modules.order.web.mapper.OrderItemMapper;
import lat.nexofood.api.modules.order.web.mapper.OrderMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantCustomerRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderTaxIntegrationTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private TaxRepository taxRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantCustomerRepository tenantCustomerRepository;
    @Mock
    private CustomerAddressRepository customerAddressRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductPricingService pricingService;

    private TaxCalculationService taxCalculationService;
    private OrderItemMapper orderItemMapper;
    private OrderMapper orderMapper;
    private OrderServiceImpl orderService;
    private TaxServiceImpl taxService;
    private ProductServiceImpl productService;

    private Tenant tenantA;
    private Tenant tenantB;
    private Tax taxA;

    @BeforeEach
    void setUp() {
        taxCalculationService = new TaxCalculationService();
        orderItemMapper = new OrderItemMapper(pricingService, taxCalculationService);
        orderMapper = new OrderMapper(orderItemMapper);

        orderService = new OrderServiceImpl(
                orderRepository,
                productRepository,
                tenantRepository,
                tenantCustomerRepository,
                customerAddressRepository,
                pricingService,
                taxCalculationService,
                orderMapper,
                orderItemMapper
        );

        TaxMapper taxMapper = new TaxMapper();
        taxService = new TaxServiceImpl(
                taxRepository,
                tenantRepository,
                productRepository,
                orderItemRepository,
                taxMapper
        );

        ProductPriceMapper productPriceMapper = new ProductPriceMapper();
        ProductMapper productMapper = new ProductMapper(productPriceMapper, pricingService);
        productService = new ProductServiceImpl(
                productRepository,
                tenantRepository,
                categoryRepository,
                taxRepository,
                productMapper
        );

        tenantA = Tenant.builder().id(UUID.randomUUID()).name("Restaurante A").build();
        tenantB = Tenant.builder().id(UUID.randomUUID()).name("Restaurante B").build();

        taxA = Tax.builder()
                .id(UUID.randomUUID())
                .tenant(tenantA)
                .name("IGV 18%")
                .rate(new BigDecimal("18.00"))
                .code("IGV")
                .isInclusive(true)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Caso 1: Nueva venta con impuesto - Fotografía histórica guardada en OrderItem")
    void testCase1_NewSaleWithTaxSnapshot() {
        Product burger = Product.builder()
                .id(UUID.randomUUID())
                .tenant(tenantA)
                .name("Hamburguesa Doble")
                .tax(taxA)
                .isAvailable(true)
                .build();

        when(tenantRepository.findById(tenantA.getId())).thenReturn(Optional.of(tenantA));
        when(productRepository.findByIdAndTenantId(burger.getId(), tenantA.getId())).thenReturn(Optional.of(burger));
        when(pricingService.calculateCurrentPrice(burger)).thenReturn(new BigDecimal("20.00"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderCreateRequest request = OrderCreateRequest.builder()
                .tenantId(tenantA.getId())
                .deliveryType(DeliveryType.TAKEAWAY)
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(burger.getId())
                                .quantity(2)
                                .notes("Sin cebolla")
                                .build()
                ))
                .build();

        OrderResponse response = orderService.create(request, null);

        assertNotNull(response);
        // Para precio 20.00 con IGV 18% incluido, qty=2:
        // base unitaria = 16.95, subtotal = 33.90, tax = 6.10, total = 40.00
        assertEquals(new BigDecimal("33.90"), response.subtotal());
        assertEquals(new BigDecimal("6.10"), response.taxTotal());
        assertEquals(new BigDecimal("40.00"), response.total());

        assertEquals(1, response.items().size());
        OrderItemResponse item = response.items().getFirst();
        assertEquals(taxA.getId(), item.taxId());
        assertEquals(new BigDecimal("18.00"), item.taxRate());
        assertEquals(new BigDecimal("6.10"), item.taxAmount());
        assertEquals(new BigDecimal("16.95"), item.unitPrice());
        assertEquals(new BigDecimal("33.90"), item.subtotal());
    }

    @Test
    @DisplayName("Caso 2: Inmutabilidad histórica - Modificar o desactivar Tax después no altera pedidos previos")
    void testCase2_HistoricalImmutability() {
        // Venta histórica ya congelada
        OrderItem historicalItem = OrderItem.builder()
                .id(UUID.randomUUID())
                .tenant(tenantA)
                .productName("Ceviche Clásico")
                .tax(taxA)
                .taxRate(new BigDecimal("18.00"))
                .taxAmount(new BigDecimal("9.00"))
                .unitPrice(new BigDecimal("41.00"))
                .quantity(1)
                .subtotal(new BigDecimal("41.00"))
                .build();

        Order historicalOrder = Order.builder()
                .id(UUID.randomUUID())
                .tenant(tenantA)
                .subtotal(new BigDecimal("41.00"))
                .taxTotal(new BigDecimal("9.00"))
                .deliveryFee(BigDecimal.ZERO)
                .total(new BigDecimal("50.00"))
                .items(List.of(historicalItem))
                .build();
        historicalItem.setOrder(historicalOrder);

        // Simulamos que el impuesto cambia a futuro (se desactiva o se crea otra tasa)
        taxA.setIsActive(false);
        taxA.setRate(new BigDecimal("20.00"));

        // El pedido histórico debe proyectar exactamente sus valores congelados
        OrderResponse response = orderMapper.toResponse(historicalOrder);
        assertEquals(new BigDecimal("41.00"), response.subtotal());
        assertEquals(new BigDecimal("9.00"), response.taxTotal());
        assertEquals(new BigDecimal("50.00"), response.total());

        OrderItemResponse itemResponse = response.items().getFirst();
        assertEquals(new BigDecimal("18.00"), itemResponse.taxRate(), "La tasa congelada debe permanecer en 18.00%");
        assertEquals(new BigDecimal("9.00"), itemResponse.taxAmount(), "El monto congelado no debe recalcularse");
    }

    @Test
    @DisplayName("Caso 3: Validación multi-tenant - Intento de usar producto de otro tenant es rechazado")
    void testCase3_MultiTenantCrossTenantRejected() {
        Product productTenantB = Product.builder()
                .id(UUID.randomUUID())
                .tenant(tenantB)
                .name("Pizza de Tenant B")
                .isAvailable(true)
                .build();

        when(tenantRepository.findById(tenantA.getId())).thenReturn(Optional.of(tenantA));
        // El repositorio no encuentra el producto bajo el tenant A
        when(productRepository.findByIdAndTenantId(productTenantB.getId(), tenantA.getId())).thenReturn(Optional.empty());

        OrderCreateRequest request = OrderCreateRequest.builder()
                .tenantId(tenantA.getId())
                .deliveryType(DeliveryType.DELIVERY)
                .items(List.of(
                        OrderItemRequest.builder()
                                .productId(productTenantB.getId())
                                .quantity(1)
                                .build()
                ))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> orderService.create(request, null));
        assertTrue(ex.getMessage().contains("no existe o no pertenece a la tienda actual"));
    }

    @Test
    @DisplayName("Caso 4: Intento de manipulación desde frontend - Los impuestos son calculados exclusivamente en backend")
    void testCase4_BackendCalculatesTaxPreventingFrontendTampering() {
        Product sushi = Product.builder()
                .id(UUID.randomUUID())
                .tenant(tenantA)
                .name("Sushi Roll")
                .tax(taxA)
                .isAvailable(true)
                .build();

        when(tenantRepository.findById(tenantA.getId())).thenReturn(Optional.of(tenantA));
        when(productRepository.findByIdAndTenantId(sushi.getId(), tenantA.getId())).thenReturn(Optional.of(sushi));
        when(pricingService.calculateCurrentPrice(sushi)).thenReturn(new BigDecimal("59.00")); // 50 base + 9 tax
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // OrderItemRequest no expone campos de impuesto; solo pide productId y quantity
        OrderItemRequest itemRequest = OrderItemRequest.builder()
                .productId(sushi.getId())
                .quantity(1)
                .build();

        OrderResponse response = orderService.create(
                OrderCreateRequest.builder()
                        .tenantId(tenantA.getId())
                        .deliveryType(DeliveryType.TAKEAWAY)
                        .items(List.of(itemRequest))
                        .build(),
                null
        );

        // El backend calculó automáticamente 18% sobre 59.00 = base 50.00, tax 9.00
        assertEquals(new BigDecimal("50.00"), response.subtotal());
        assertEquals(new BigDecimal("9.00"), response.taxTotal());
        assertEquals(new BigDecimal("59.00"), response.total());
    }

    @Test
    @DisplayName("Caso 5: Producto con impuesto inactivo o sin impuesto - Se registra con tasa 0% sin romper la venta")
    void testCase5_ProductWithoutTaxOrInactiveTax() {
        Product water = Product.builder()
                .id(UUID.randomUUID())
                .tenant(tenantA)
                .name("Agua Mineral")
                .tax(null) // Exento / sin impuesto
                .isAvailable(true)
                .build();

        when(tenantRepository.findById(tenantA.getId())).thenReturn(Optional.of(tenantA));
        when(productRepository.findByIdAndTenantId(water.getId(), tenantA.getId())).thenReturn(Optional.of(water));
        when(pricingService.calculateCurrentPrice(water)).thenReturn(new BigDecimal("5.00"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.create(
                OrderCreateRequest.builder()
                        .tenantId(tenantA.getId())
                        .deliveryType(DeliveryType.TAKEAWAY)
                        .items(List.of(OrderItemRequest.builder().productId(water.getId()).quantity(3).build()))
                        .build(),
                null
        );

        assertEquals(new BigDecimal("15.00"), response.subtotal());
        assertEquals(new BigDecimal("0.00"), response.taxTotal());
        assertEquals(new BigDecimal("15.00"), response.total());

        OrderItemResponse item = response.items().getFirst();
        assertNull(item.taxId());
        assertEquals(new BigDecimal("0.00"), item.taxRate());
        assertEquals(new BigDecimal("0.00"), item.taxAmount());
        assertEquals(new BigDecimal("5.00"), item.unitPrice());
    }

    @Test
    @DisplayName("Caso 6: Regla de negocio - TaxService prohíbe modificar rate si el impuesto está en uso")
    void testCase6_CannotModifyTaxRateWhenInUse() {
        when(taxRepository.findByIdAndTenantId(taxA.getId(), tenantA.getId())).thenReturn(Optional.of(taxA));
        // El impuesto ya está en uso en algún producto
        when(productRepository.existsByTaxId(taxA.getId())).thenReturn(true);

        TaxRequest updateRequest = TaxRequest.builder()
                .name("IGV Modificado")
                .rate(new BigDecimal("21.00")) // Intento de cambiar la tasa
                .build();

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> taxService.update(tenantA.getId(), taxA.getId(), updateRequest)
        );
        assertTrue(ex.getMessage().contains("No se puede modificar la tasa (rate) de un impuesto que ya está en uso"));
    }

    @Test
    @DisplayName("Caso 7: ProductService rechaza asociar impuesto de otro tenant")
    void testCase7_ProductCannotUseTaxFromAnotherTenant() {
        Tax taxTenantB = Tax.builder()
                .id(UUID.randomUUID())
                .tenant(tenantB)
                .name("Tax Tenant B")
                .rate(new BigDecimal("15.00"))
                .isActive(true)
                .build();

        when(tenantRepository.findById(tenantA.getId())).thenReturn(Optional.of(tenantA));
        // Cuando busca el impuesto en tenantA, no lo encuentra porque pertenece a tenantB
        when(taxRepository.findByIdAndTenantId(taxTenantB.getId(), tenantA.getId())).thenReturn(Optional.empty());

        ProductRequest request = ProductRequest.builder()
                .name("Nuevo Producto")
                .taxId(taxTenantB.getId())
                .build();

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> productService.create(tenantA.getId(), request)
        );
        assertTrue(ex.getMessage().contains("no existe o no pertenece al tenant actual"));
    }
}
