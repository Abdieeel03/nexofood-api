package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.common.audit.SecurityAuditLogger;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.modules.catalog.application.service.CalculateProductPriceService;
import lat.nexofood.api.modules.catalog.application.service.GetBasePriceService;
import lat.nexofood.api.modules.catalog.application.service.GetEffectivePriceRuleService;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingService;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingServiceImpl;
import lat.nexofood.api.modules.catalog.application.service.ProductStockService;
import lat.nexofood.api.modules.catalog.application.service.TaxCalculationService;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.CustomerAddressRepository;
import lat.nexofood.api.modules.notification.application.service.NotificationService;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.infrastructure.repository.OrderRepository;
import lat.nexofood.api.modules.order.web.dto.request.OrderCreateRequest;
import lat.nexofood.api.modules.order.web.dto.request.OrderItemRequest;
import lat.nexofood.api.modules.order.web.dto.response.OrderResponse;
import lat.nexofood.api.modules.order.web.mapper.OrderItemMapper;
import lat.nexofood.api.modules.order.web.mapper.OrderMapper;
import lat.nexofood.api.modules.payment.application.service.PaymentRefundService;
import lat.nexofood.api.modules.store.application.service.customer.CurrentCustomerResolver;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.domain.TenantCustomerId;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantCustomerRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.subscription.application.service.guard.SubscriptionGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderScheduleAndNotificationIntegrationTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantCustomerRepository tenantCustomerRepository;
    @Mock
    private TenantMemberRepository tenantMemberRepository;
    @Mock
    private CustomerAddressRepository customerAddressRepository;
    @Mock
    private CurrentCustomerResolver currentCustomerResolver;
    @Mock
    private SubscriptionGuard subscriptionGuard;
    @Mock
    private PaymentGuard paymentGuard;
    @Mock
    private TenantAccessService tenantAccessService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ProductStockService productStockService;
    @Mock
    private PaymentRefundService paymentRefundService;
    @Mock
    private NotificationService notificationService;

    private OrderServiceImpl orderService;
    private UUID tenantId;
    private UUID userId;
    private Tenant tenant;
    private TenantCustomer customer;
    private Product burger;

    @BeforeEach
    void setUp() {
        GetBasePriceService basePriceService = new GetBasePriceService();
        GetEffectivePriceRuleService effectiveRuleService = new GetEffectivePriceRuleService(basePriceService);
        CalculateProductPriceService calculatePriceService = new CalculateProductPriceService(effectiveRuleService);
        ProductPricingService pricingService = new ProductPricingServiceImpl(
                calculatePriceService, basePriceService, effectiveRuleService
        );
        TaxCalculationService taxCalculationService = new TaxCalculationService();
        OrderItemMapper orderItemMapper = new OrderItemMapper(pricingService, taxCalculationService);
        OrderMapper orderMapper = new OrderMapper(orderItemMapper);

        orderService = new OrderServiceImpl(
                orderRepository,
                productRepository,
                tenantRepository,
                tenantCustomerRepository,
                tenantMemberRepository,
                customerAddressRepository,
                pricingService,
                taxCalculationService,
                orderMapper,
                orderItemMapper,
                currentCustomerResolver,
                subscriptionGuard,
                new OrderStateMachine(),
                paymentGuard,
                tenantAccessService,
                currentUserProvider,
                productStockService,
                paymentRefundService,
                new SecurityAuditLogger(),
                null,
                null,
                notificationService
        );

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        LocalTime now = LocalTime.now();

        tenant = Tenant.builder()
                .id(tenantId)
                .name("Pollería Nexo")
                .slug("polleria-nexo")
                .isActive(true)
                .openingTime(now.minusHours(2))
                .closingTime(now.plusHours(2))
                .isOpen(true)
                .build();

        User user = User.builder()
                .id(userId)
                .email("cliente@nexofood.lat")
                .fullName("Cliente Prueba")
                .isActive(true)
                .build();

        customer = TenantCustomer.builder()
                .id(new TenantCustomerId(tenantId, userId))
                .tenant(tenant)
                .user(user)
                .isBlocked(false)
                .build();

        burger = Product.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name("1/4 Pollo a la Brasa")
                .stock(10)
                .isAvailable(true)
                .prices(new ArrayList<>(List.of(
                        ProductPrice.builder()
                                .id(UUID.randomUUID())
                                .name("Precio Base")
                                .price(new BigDecimal("25.00"))
                                .isBase(true)
                                .isActive(true)
                                .priority(0)
                                .build()
                )))
                .build();
    }

    @Test
    @DisplayName("create: rechaza el pedido si el restaurante tiene isOpen = false")
    void createRejectsOrderWhenTenantIsMarkedClosed() {
        tenant.setIsOpen(false);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        OrderCreateRequest request = OrderCreateRequest.builder()
                .tenantId(tenantId)
                .deliveryType(DeliveryType.TAKEAWAY)
                .items(List.of(OrderItemRequest.builder().productId(burger.getId()).quantity(1).build()))
                .build();

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> orderService.create(request, userId)
        );
        assertTrue(ex.getMessage().contains("cerrado"));
        verify(orderRepository, never()).save(any());
        verify(notificationService, never()).notifyNewOrder(any());
    }

    @Test
    @DisplayName("create y buildDraft: rechazan pedidos cuando la hora actual está fuera del horario de atención")
    void createAndBuildDraftRejectOrderOutsideOperatingHours() {
        LocalTime now = LocalTime.now();
        tenant.setIsOpen(true);
        tenant.setOpeningTime(now.plusHours(2));
        tenant.setClosingTime(now.plusHours(4));

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        OrderCreateRequest request = OrderCreateRequest.builder()
                .tenantId(tenantId)
                .deliveryType(DeliveryType.TAKEAWAY)
                .items(List.of(OrderItemRequest.builder().productId(burger.getId()).quantity(1).build()))
                .build();

        BadRequestException createEx = assertThrows(
                BadRequestException.class,
                () -> orderService.create(request, userId)
        );
        assertTrue(createEx.getMessage().contains("cerrado"));

        BadRequestException draftEx = assertThrows(
                BadRequestException.class,
                () -> orderService.buildDraft(request, null, customer)
        );
        assertTrue(draftEx.getMessage().contains("cerrado"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: cuando la tienda está abierta, guarda la orden y notifica NEW_ORDER_RECEIVED")
    void createWhenStoreIsOpenSavesOrderAndNotifiesNewOrder() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantCustomerRepository.findById(new TenantCustomerId(tenantId, userId)))
                .thenReturn(Optional.of(customer));
        when(productRepository.findByIdAndTenantId(burger.getId(), tenantId)).thenReturn(Optional.of(burger));
        when(orderRepository.countByTenantId(tenantId)).thenReturn(0L);
        when(orderRepository.existsByOrderNumberAndTenantId("ORD-000001", tenantId)).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        OrderCreateRequest request = OrderCreateRequest.builder()
                .tenantId(tenantId)
                .deliveryType(DeliveryType.TAKEAWAY)
                .items(List.of(OrderItemRequest.builder().productId(burger.getId()).quantity(2).build()))
                .build();

        OrderResponse response = orderService.create(request, userId);

        assertEquals("ORD-000001", response.orderNumber());
        verify(notificationService).notifyNewOrder(any(Order.class));
    }

    @Test
    @DisplayName("updateStatus: notifica al cliente ORDER_STATUS_CHANGED al cambiar estado")
    void updateStatusNotifiesCustomer() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .tenant(tenant)
                .customer(customer)
                .customerId(userId)
                .orderNumber("ORD-000020")
                .deliveryType(DeliveryType.TAKEAWAY)
                .status(OrderStatus.PENDIENTE)
                .items(List.of())
                .build();

        when(orderRepository.findByIdAndTenantIdForUpdate(orderId, tenantId)).thenReturn(Optional.of(order));
        when(tenantAccessService.isSuperAdmin()).thenReturn(false);
        when(tenantAccessService.getCurrentUserRoleInTenant(tenantId)).thenReturn(Optional.of(TenantStaffRole.OWNER));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse response = orderService.updateStatus(tenantId, orderId, OrderStatus.EN_PREPARACION);

        assertEquals(OrderStatus.EN_PREPARACION, response.status());
        verify(notificationService).notifyOrderStatusChanged(order);
    }

    @Test
    @DisplayName("cancelByCustomer: notifica al cliente ORDER_STATUS_CHANGED al cancelar pedido")
    void cancelByCustomerNotifiesOrderStatusChanged() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .tenant(tenant)
                .customer(customer)
                .customerId(userId)
                .orderNumber("ORD-000021")
                .deliveryType(DeliveryType.DELIVERY)
                .status(OrderStatus.PENDIENTE)
                .total(new BigDecimal("50.00"))
                .items(List.of())
                .build();

        when(currentCustomerResolver.resolveBySlug("polleria-nexo")).thenReturn(customer);
        when(orderRepository.findByIdAndTenantIdAndCustomerUserIdForUpdate(orderId, tenantId, userId))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse response = orderService.cancelByCustomer("polleria-nexo", orderId, "Cambio de planes");

        assertEquals(OrderStatus.CANCELADO, response.status());
        verify(notificationService).notifyOrderStatusChanged(order);
    }
}
