package lat.nexofood.api.modules.payment.application.service;

import lat.nexofood.api.common.config.TenantConnectionPreparer;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.modules.cart.domain.Cart;
import lat.nexofood.api.modules.cart.domain.CartItem;
import lat.nexofood.api.modules.cart.infrastructure.repository.CartItemRepository;
import lat.nexofood.api.modules.cart.infrastructure.repository.CartRepository;
import lat.nexofood.api.modules.order.application.dto.OrderDraft;
import lat.nexofood.api.modules.order.application.dto.OrderDraftItem;
import lat.nexofood.api.modules.order.application.service.OrderService;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.infrastructure.repository.OrderRepository;
import lat.nexofood.api.modules.payment.domain.Payment;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.FrontendProperties;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoClient;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoProperties;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderRequest;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderResponse;
import lat.nexofood.api.modules.payment.infrastructure.repository.PaymentRepository;
import lat.nexofood.api.modules.payment.web.dto.response.CheckoutPaymentResponse;
import lat.nexofood.api.modules.payment.web.mapper.PaymentMapper;
import lat.nexofood.api.modules.store.application.service.customer.CurrentCustomerResolver;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.domain.TenantCustomerId;
import lat.nexofood.api.modules.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderService orderService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private MercadoPagoClient mercadoPagoClient;
    @Mock
    private MercadoPagoOAuthService oauthService;
    @Mock
    private PaymentRefundService paymentRefundService;
    @Mock
    private CurrentCustomerResolver currentCustomerResolver;
    @Mock
    private TenantConnectionPreparer tenantConnectionPreparer;

    private OrderPaymentServiceImpl orderPaymentService;
    private Tenant tenant;
    private User customerUser;
    private TenantCustomer customer;
    private OrderDraft draft;

    @BeforeEach
    void setUp() {
        MercadoPagoProperties properties = MercadoPagoProperties.builder()
                .currency("PEN")
                .orderExpiration("PT30M")
                .build();
        FrontendProperties frontendProperties = new FrontendProperties();

        orderPaymentService = new OrderPaymentServiceImpl(
                paymentRepository,
                orderService,
                orderRepository,
                cartRepository,
                cartItemRepository,
                mercadoPagoClient,
                oauthService,
                paymentRefundService,
                properties,
                frontendProperties,
                currentCustomerResolver,
                new PaymentMapper(),
                tenantConnectionPreparer
        );

        tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Burger Bros")
                .slug("burger-bros")
                .mpAccessToken("APP_USR-tenant-token")
                .isActive(true)
                .build();

        customerUser = User.builder()
                .id(UUID.randomUUID())
                .email("buyer@nexofood.lat")
                .fullName("Comprador")
                .build();

        customer = TenantCustomer.builder()
                .id(new TenantCustomerId(tenant.getId(), customerUser.getId()))
                .tenant(tenant)
                .user(customerUser)
                .build();

        OrderDraftItem item = OrderDraftItem.builder()
                .productId(UUID.randomUUID())
                .productName("Burger Suprema")
                .unitPrice(new BigDecimal("50.00"))
                .quantity(1)
                .lineSubtotal(new BigDecimal("50.00"))
                .taxRate(new BigDecimal("18.00"))
                .taxAmount(new BigDecimal("9.00"))
                .build();

        draft = OrderDraft.builder()
                .tenantId(tenant.getId())
                .customerUserId(customerUser.getId())
                .deliveryType(DeliveryType.DELIVERY)
                .subtotal(new BigDecimal("50.00"))
                .taxTotal(new BigDecimal("9.00"))
                .deliveryFee(new BigDecimal("6.50"))
                .total(new BigDecimal("65.50"))
                .items(List.of(item))
                .build();
    }

    @Test
    @DisplayName("startCheckout: garantiza que la suma de items en MpOrderRequest coincida exactamente con total_amount")
    void startCheckoutEnsuresItemsSumMatchesTotalAmount() {
        when(oauthService.getValidAccessToken(tenant)).thenReturn("APP_USR-tenant-token");
        when(paymentRepository.findAllByTenantIdAndCustomerIdAndStatusIn(any(), any(), any()))
                .thenReturn(List.of());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });

        ArgumentCaptor<MpOrderRequest> mpReqCaptor = ArgumentCaptor.forClass(MpOrderRequest.class);
        when(mercadoPagoClient.createOrder(eq("APP_USR-tenant-token"), any(), mpReqCaptor.capture()))
                .thenReturn(MpOrderResponse.builder()
                        .id("ORD-MP-777")
                        .status("created")
                        .statusDetail("created")
                        .checkoutUrl("https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=777")
                        .build());

        CheckoutPaymentResponse response = orderPaymentService.startCheckout(draft, customer);

        assertEquals("ORD-MP-777", response.mpOrderId());
        assertEquals(new BigDecimal("65.50"), response.amount());

        MpOrderRequest sent = mpReqCaptor.getValue();
        assertEquals("online", sent.type());
        assertEquals("manual", sent.processingMode());
        assertEquals("65.50", sent.totalAmount());
        assertEquals(2, sent.items().size()); // 1 línea de producto (59.00) + 1 línea de envío (6.50)

        BigDecimal sumItems = sent.items().stream()
                .map(i -> new BigDecimal(i.unitPrice()).multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("65.50"), sumItems);
    }

    @Test
    @DisplayName("processMpOrderNotification: webhook processed/accredited aprueba el pago, crea una única orden de forma idempotente y vacía el carrito")
    void webhookProcessedCreatesOrderOnceAndClearsCart() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .tenant(tenant)
                .customerId(customerUser.getId())
                .mpOrderId("ORD-MP-777")
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("65.50"))
                .checkoutSnapshot(draft)
                .build();

        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .customer(customer)
                .items(new ArrayList<>(List.of(CartItem.builder().id(UUID.randomUUID()).build())))
                .build();

        Order createdOrder = Order.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .orderNumber("ORD-000001")
                .status(OrderStatus.PENDIENTE)
                .total(new BigDecimal("65.50"))
                .build();

        when(paymentRepository.findByMpOrderIdForUpdate("ORD-MP-777")).thenReturn(Optional.of(payment));
        when(oauthService.getValidAccessToken(tenant)).thenReturn("APP_USR-tenant-token");
        when(mercadoPagoClient.getOrder("APP_USR-tenant-token", "ORD-MP-777")).thenReturn(
                MpOrderResponse.builder()
                        .id("ORD-MP-777")
                        .status("processed")
                        .statusDetail("accredited")
                        .externalReference(paymentId.toString())
                        .totalAmount(new BigDecimal("65.50"))
                        .totalPaidAmount(new BigDecimal("65.50"))
                        .rawJson("{\"id\":\"ORD-MP-777\",\"status\":\"processed\",\"payer\":{\"email\":\"secret@mail.com\"}}")
                        .build()
        );
        when(orderService.createEntityFromApprovedPayment(payment)).thenReturn(createdOrder);
        when(cartRepository.findByTenantIdAndCustomerUserId(tenant.getId(), customerUser.getId()))
                .thenReturn(Optional.of(cart));

        // 1ra notificación del webhook
        orderPaymentService.processMpOrderNotification("ORD-MP-777");

        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
        assertNotNull(payment.getOrder());
        assertEquals("ORD-000001", payment.getOrder().getOrderNumber());
        assertTrue(cart.getItems().isEmpty());
        verify(tenantConnectionPreparer).setTenantContext(tenant.getId());
        // Verifica que rawResponse fue sanitizado (no incluye payer.email)
        assertNotNull(payment.getRawResponse());
        assertTrue(!payment.getRawResponse().contains("secret@mail.com"));

        // 2da notificación duplicada del mismo webhook -> idempotente, NO vuelve a crear otra orden
        orderPaymentService.processMpOrderNotification("ORD-MP-777");
        verify(orderService, times(1)).createEntityFromApprovedPayment(payment);
    }

    @Test
    @DisplayName("processMpOrderNotification: si falla la creación de la orden tras pago aprobado, reembolsa automáticamente al cliente")
    void webhookTriggersAutomaticRefundIfOrderCreationFails() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .tenant(tenant)
                .customerId(customerUser.getId())
                .mpOrderId("ORD-MP-FAIL")
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("65.50"))
                .checkoutSnapshot(draft)
                .build();

        when(paymentRepository.findByMpOrderIdForUpdate("ORD-MP-FAIL")).thenReturn(Optional.of(payment));
        when(oauthService.getValidAccessToken(tenant)).thenReturn("APP_USR-tenant-token");
        when(mercadoPagoClient.getOrder("APP_USR-tenant-token", "ORD-MP-FAIL")).thenReturn(
                MpOrderResponse.builder()
                        .id("ORD-MP-FAIL")
                        .status("processed")
                        .statusDetail("accredited")
                        .externalReference(paymentId.toString())
                        .totalPaidAmount(new BigDecimal("65.50"))
                        .build()
        );
        when(orderService.createEntityFromApprovedPayment(payment))
                .thenThrow(new BadRequestException("Producto agotado"));

        orderPaymentService.processMpOrderNotification("ORD-MP-FAIL");

        assertNull(payment.getOrder());
        verify(paymentRefundService).refundPaymentDirectly(eq(payment), any());
        verify(cartRepository, never()).findByTenantIdAndCustomerUserId(any(), any());
    }
}
