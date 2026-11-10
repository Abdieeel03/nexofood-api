package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ForbiddenException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.common.util.GeoUtils;
import lat.nexofood.api.modules.cart.domain.Cart;
import lat.nexofood.api.modules.cart.domain.CartItem;
import lat.nexofood.api.modules.cart.infrastructure.repository.CartItemRepository;
import lat.nexofood.api.modules.cart.infrastructure.repository.CartRepository;
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
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.identity.domain.CustomerAddress;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.CustomerAddressRepository;
import lat.nexofood.api.modules.order.application.dto.OrderDraft;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.infrastructure.repository.OrderRepository;
import lat.nexofood.api.modules.order.web.dto.request.OrderCheckoutRequest;
import lat.nexofood.api.modules.order.web.dto.response.OrderResponse;
import lat.nexofood.api.modules.order.web.mapper.OrderItemMapper;
import lat.nexofood.api.modules.order.web.mapper.OrderMapper;
import lat.nexofood.api.modules.payment.application.service.OrderPaymentService;
import lat.nexofood.api.modules.payment.application.service.PaymentRefundService;
import lat.nexofood.api.modules.payment.domain.Payment;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lat.nexofood.api.modules.payment.infrastructure.repository.PaymentRepository;
import lat.nexofood.api.modules.payment.web.dto.response.CheckoutPaymentResponse;
import lat.nexofood.api.modules.store.application.service.customer.CurrentCustomerResolver;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.domain.TenantCustomerId;
import lat.nexofood.api.modules.store.domain.TenantMember;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

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
    private PaymentRepository paymentRepository;
    @Mock
    private TenantAccessService tenantAccessService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private OrderPaymentService orderPaymentService;
    @Mock
    private PaymentRefundService paymentRefundService;

    private OrderServiceImpl orderService;
    private CheckoutServiceImpl checkoutService;

    private Tenant tenant;
    private User customerUser;
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
        OrderStateMachine orderStateMachine = new OrderStateMachine();
        PaymentGuard paymentGuard = new DefaultPaymentGuard(paymentRepository);

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
                orderStateMachine,
                paymentGuard,
                tenantAccessService,
                currentUserProvider,
                null,
                paymentRefundService,
                null
        );

        checkoutService = new CheckoutServiceImpl(
                currentCustomerResolver,
                cartRepository,
                orderService,
                orderPaymentService
        );

        tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Burger Bros")
                .slug("burger-bros")
                .location(GeoUtils.createPoint(-12.1191, -77.0282)) // Miraflores
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .defaultDeliveryFee(new BigDecimal("6.50"))
                .isActive(true)
                .build();

        customerUser = User.builder()
                .id(UUID.randomUUID())
                .email("lucia@nexofood.lat")
                .fullName("Lucía Cliente")
                .phone("+51987654321")
                .isActive(true)
                .build();

        customer = TenantCustomer.builder()
                .id(new TenantCustomerId(tenant.getId(), customerUser.getId()))
                .tenant(tenant)
                .user(customerUser)
                .loyaltyPoints(10)
                .totalOrders(0)
                .isBlocked(false)
                .build();

        Category activeCategory = Category.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name("Hamburguesas")
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

        ProductPrice basePrice = ProductPrice.builder()
                .id(UUID.randomUUID())
                .name("Precio Base")
                .price(new BigDecimal("59.00")) // 50.00 base + 9.00 IGV
                .isBase(true)
                .isActive(true)
                .priority(0)
                .build();

        burger = Product.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .category(activeCategory)
                .tax(igv)
                .name("Burger Suprema")
                .isAvailable(true)
                .prices(new ArrayList<>(List.of(basePrice)))
                .build();
    }

    @Test
    @DisplayName("CheckoutService.createFromCart: valida cobertura, congela snapshot con impuestos y deliveryFee, delega a OrderPaymentService y luego crea orden al aprobarse")
    void checkoutFromCartBuildsDraftAndStartsCheckoutThenWebhookCreatesOrder() {
        UUID addressId = UUID.randomUUID();
        CustomerAddress address = CustomerAddress.builder()
                .id(addressId)
                .user(customerUser)
                .title("Casa")
                .addressLine("Av. Pardo 450, Miraflores")
                .location(GeoUtils.createPoint(-12.1210, -77.0300)) // ~0.29 km del local (< 5.00 km)
                .build();

        CartItem cartItem = CartItem.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .product(burger)
                .quantity(1)
                .unitPrice(new BigDecimal("59.00"))
                .subtotal(new BigDecimal("59.00"))
                .notes("Sin pepinillos")
                .build();

        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .customer(customer)
                .items(new ArrayList<>(List.of(cartItem)))
                .total(new BigDecimal("59.00"))
                .notes("Tocar intercomunicador 101")
                .build();
        cartItem.setCart(cart);

        when(currentCustomerResolver.resolveBySlug("burger-bros")).thenReturn(customer);
        when(cartRepository.findByTenantIdAndCustomerUserId(tenant.getId(), customerUser.getId()))
                .thenReturn(Optional.of(cart));
        when(customerAddressRepository.findByIdAndUserId(addressId, customerUser.getId()))
                .thenReturn(Optional.of(address));
        when(customerAddressRepository.findById(addressId)).thenReturn(Optional.of(address));
        when(productRepository.findByIdAndTenantId(burger.getId(), tenant.getId()))
                .thenReturn(Optional.of(burger));
        when(orderRepository.countByTenantId(tenant.getId())).thenReturn(0L);
        when(orderRepository.existsByOrderNumberAndTenantId("ORD-000001", tenant.getId())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        UUID paymentId = UUID.randomUUID();
        final OrderDraft[] capturedDraft = new OrderDraft[1];
        when(orderPaymentService.startCheckout(any(OrderDraft.class), eq(customer))).thenAnswer(inv -> {
            capturedDraft[0] = inv.getArgument(0);
            return CheckoutPaymentResponse.builder()
                    .paymentId(paymentId)
                    .mpOrderId("ORD-MP-123")
                    .checkoutUrl("https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=123")
                    .status(PaymentStatus.PENDING)
                    .amount(capturedDraft[0].total())
                    .currency("PEN")
                    .build();
        });

        OrderCheckoutRequest request = OrderCheckoutRequest.builder()
                .deliveryType(DeliveryType.DELIVERY)
                .customerAddressId(addressId)
                .ruc("20123456789")
                .build();

        CheckoutPaymentResponse checkoutResp = checkoutService.createFromCart("burger-bros", request);

        assertEquals(paymentId, checkoutResp.paymentId());
        assertEquals(new BigDecimal("65.50"), checkoutResp.amount());
        assertNotNull(capturedDraft[0]);

        // El carrito no se vacía aún durante el checkout (se vacía al aprobarse el pago)
        assertFalse(cart.getItems().isEmpty());

        // Simulamos la aprobación del pago en el webhook para crear la orden desde el snapshot congelado
        Payment approvedPayment = Payment.builder()
                .id(paymentId)
                .tenant(tenant)
                .customerId(customerUser.getId())
                .status(PaymentStatus.APPROVED)
                .amount(new BigDecimal("65.50"))
                .checkoutSnapshot(capturedDraft[0])
                .build();
        when(tenantCustomerRepository.findById(new TenantCustomerId(tenant.getId(), customerUser.getId())))
                .thenReturn(Optional.of(customer));

        OrderResponse response = orderService.createFromApprovedPayment(approvedPayment);

        assertEquals("ORD-000001", response.orderNumber());
        assertEquals("20123456789", response.ruc());
        assertEquals(DeliveryType.DELIVERY, response.deliveryType());
        assertEquals(OrderStatus.PENDIENTE, response.status());
        assertEquals("Av. Pardo 450, Miraflores", response.deliveryAddress());
        assertEquals(new BigDecimal("50.00"), response.subtotal());
        assertEquals(new BigDecimal("9.00"), response.taxTotal());
        assertEquals(new BigDecimal("6.50"), response.deliveryFee());
        assertEquals(new BigDecimal("65.50"), response.total());
        assertEquals("Tocar intercomunicador 101", response.notes());
    }

    @Test
    @DisplayName("CheckoutService.createFromCart: rechaza entrega DELIVERY fuera del radio de cobertura del restaurante")
    void checkoutRejectsDeliveryOutsideRadius() {
        CartItem cartItem = CartItem.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .product(burger)
                .quantity(1)
                .unitPrice(new BigDecimal("59.00"))
                .build();
        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .customer(customer)
                .items(new ArrayList<>(List.of(cartItem)))
                .build();

        when(currentCustomerResolver.resolveBySlug("burger-bros")).thenReturn(customer);
        when(cartRepository.findByTenantIdAndCustomerUserId(tenant.getId(), customerUser.getId()))
                .thenReturn(Optional.of(cart));

        // Ubicación a > 12 km de Miraflores (fuera del radio de 5.00 km)
        OrderCheckoutRequest request = OrderCheckoutRequest.builder()
                .deliveryType(DeliveryType.DELIVERY)
                .deliveryAddress("Av. Universitaria 1800, San Miguel")
                .deliveryLatitude(-12.0150)
                .deliveryLongitude(-77.0850)
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                checkoutService.createFromCart("burger-bros", request)
        );
        assertTrue(ex.getMessage().contains("fuera del área de cobertura"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateStatus: valida roles (COCINERO, REPARTIDOR), PaymentGuard y suma loyaltyPoints al pasar a ENTREGADO")
    void updateStatusEnforcesStateMachinePaymentGuardAndAwardsLoyaltyPoints() {
        UUID orderId = UUID.randomUUID();
        User deliveryUser = User.builder().id(UUID.randomUUID()).fullName("Pedro Repartidor").build();

        Order order = Order.builder()
                .id(orderId)
                .tenant(tenant)
                .customer(customer)
                .deliveryStaff(deliveryUser)
                .orderNumber("ORD-000001")
                .deliveryType(DeliveryType.DELIVERY)
                .status(OrderStatus.PENDIENTE)
                .subtotal(new BigDecimal("50.00"))
                .taxTotal(new BigDecimal("9.00"))
                .deliveryFee(new BigDecimal("6.50"))
                .total(new BigDecimal("65.50"))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByIdAndTenantId(orderId, tenant.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Si existe un Payment en estado PENDING, PaymentGuard impide pasar a EN_PREPARACION
        Payment pendingPayment = Payment.builder()
                .id(UUID.randomUUID())
                .order(order)
                .tenant(tenant)
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("65.50"))
                .build();
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(pendingPayment));
        when(tenantAccessService.isSuperAdmin()).thenReturn(false);
        when(tenantAccessService.getCurrentUserRoleInTenant(tenant.getId()))
                .thenReturn(Optional.of(TenantStaffRole.COCINERO));

        assertThrows(ResourceConflictException.class, () ->
                orderService.updateStatus(tenant.getId(), orderId, OrderStatus.EN_PREPARACION)
        );

        // 2. Cuando el pago pasa a APPROVED, el COCINERO puede mover a EN_PREPARACION y luego a LISTO_PARA_ENTREGA
        pendingPayment.setStatus(PaymentStatus.APPROVED);
        OrderResponse inPrep = orderService.updateStatus(tenant.getId(), orderId, OrderStatus.EN_PREPARACION);
        assertEquals(OrderStatus.EN_PREPARACION, inPrep.status());

        OrderResponse ready = orderService.updateStatus(tenant.getId(), orderId, OrderStatus.LISTO_PARA_ENTREGA);
        assertEquals(OrderStatus.LISTO_PARA_ENTREGA, ready.status());

        // 3. El COCINERO no puede pasar a EN_CAMINO (403 Forbidden)
        assertThrows(ForbiddenException.class, () ->
                orderService.updateStatus(tenant.getId(), orderId, OrderStatus.EN_CAMINO)
        );

        // 4. El REPARTIDOR asignado pasa a EN_CAMINO y luego a ENTREGADO (sumando 65 puntos de fidelidad)
        when(tenantAccessService.getCurrentUserRoleInTenant(tenant.getId()))
                .thenReturn(Optional.of(TenantStaffRole.REPARTIDOR));
        when(currentUserProvider.getCurrentUserId()).thenReturn(deliveryUser.getId());

        OrderResponse onWay = orderService.updateStatus(tenant.getId(), orderId, OrderStatus.EN_CAMINO);
        assertEquals(OrderStatus.EN_CAMINO, onWay.status());

        OrderResponse delivered = orderService.updateStatus(tenant.getId(), orderId, OrderStatus.ENTREGADO);
        assertEquals(OrderStatus.ENTREGADO, delivered.status());
        // Tenía 10 puntos + 65 puntos (por total 65.50) = 75 puntos
        assertEquals(75, customer.getLoyaltyPoints());
        assertEquals(1, customer.getTotalOrders());
        verify(tenantCustomerRepository).save(customer);
    }

    @Test
    @DisplayName("assignDeliveryStaff: valida que el usuario sea miembro activo con rol REPARTIDOR en el tenant")
    void assignDeliveryStaffValidatesRepartidorRole() {
        UUID orderId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();
        User staffUser = User.builder().id(staffUserId).fullName("Luis Repartidor").build();

        Order order = Order.builder()
                .id(orderId)
                .tenant(tenant)
                .customer(customer)
                .orderNumber("ORD-000002")
                .deliveryType(DeliveryType.DELIVERY)
                .status(OrderStatus.LISTO_PARA_ENTREGA)
                .deliveryAddress("Av. Arequipa 4100, Miraflores")
                .deliveryLocation(GeoUtils.createPoint(-12.1040, -77.0315))
                .items(new ArrayList<>())
                .build();

        TenantMember cookMember = TenantMember.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .user(staffUser)
                .role(TenantStaffRole.COCINERO)
                .isActive(true)
                .build();

        when(orderRepository.findByIdAndTenantId(orderId, tenant.getId())).thenReturn(Optional.of(order));
        when(tenantMemberRepository.findByTenantIdAndUserIdAndIsActiveTrue(tenant.getId(), staffUserId))
                .thenReturn(Optional.of(cookMember));

        // Si el miembro es COCINERO, no se le puede asignar como repartidor
        assertThrows(BadRequestException.class, () ->
                orderService.assignDeliveryStaff(tenant.getId(), orderId, staffUserId)
        );

        // Si tiene rol REPARTIDOR, se asigna correctamente y calcula el ETA inicial entre tienda y cliente
        cookMember.setRole(TenantStaffRole.REPARTIDOR);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse assigned = orderService.assignDeliveryStaff(tenant.getId(), orderId, staffUserId);
        assertEquals(staffUserId, assigned.deliveryStaffId());
        assertEquals("Luis Repartidor", assigned.deliveryStaffName());
        assertNotNull(assigned.estimatedDeliveryMinutes());
        assertTrue(assigned.estimatedDeliveryMinutes() > 0);
        assertEquals(
                "Tu pedido estará en tu casa en ~" + assigned.estimatedDeliveryMinutes() + " min",
                assigned.etaMessage()
        );
    }
}
