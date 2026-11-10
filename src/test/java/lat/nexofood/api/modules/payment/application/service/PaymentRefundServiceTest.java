package lat.nexofood.api.modules.payment.application.service;

import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.payment.domain.Payment;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoClient;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderResponse;
import lat.nexofood.api.modules.payment.infrastructure.repository.PaymentRepository;
import lat.nexofood.api.modules.payment.web.mapper.PaymentMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRefundServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private MercadoPagoClient mercadoPagoClient;
    @Mock
    private MercadoPagoOAuthService oauthService;

    private PaymentRefundServiceImpl refundService;

    @BeforeEach
    void setUp() {
        refundService = new PaymentRefundServiceImpl(
                paymentRepository,
                mercadoPagoClient,
                oauthService,
                new PaymentMapper()
        );
    }

    @Test
    @DisplayName("refundOrderPayment: invoca POST /v1/orders/{id}/refund con llave idempotente refund-{paymentId} y marca REFUNDED")
    void refundsApprovedPaymentAndUpdatesStatus() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).mpAccessToken("APP_USR-token").build();
        Order order = Order.builder().id(orderId).tenant(tenant).build();

        Payment payment = Payment.builder()
                .id(paymentId)
                .order(order)
                .tenant(tenant)
                .mpOrderId("ORD-MP-500")
                .status(PaymentStatus.APPROVED)
                .amount(new BigDecimal("40.00"))
                .build();

        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(payment));
        when(oauthService.getValidAccessToken(tenant)).thenReturn("APP_USR-token");
        when(mercadoPagoClient.refundOrder("APP_USR-token", "refund-" + paymentId, "ORD-MP-500"))
                .thenReturn(MpOrderResponse.builder()
                        .id("ORD-MP-500")
                        .status("refunded")
                        .statusDetail("refunded")
                        .rawJson("{\"id\":\"ORD-MP-500\",\"status\":\"refunded\"}")
                        .build());

        refundService.refundOrderPayment(order, "Cancelado por cliente");

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        assertNotNull(payment.getRefundedAt());
        assertEquals("Cancelado por cliente", payment.getRefundReason());
        verify(paymentRepository).save(payment);

        // Si ya está REFUNDED, una segunda llamada es idempotente y no vuelve a llamar a Mercado Pago
        refundService.refundOrderPayment(order, "Segundo intento");
        verify(mercadoPagoClient).refundOrder("APP_USR-token", "refund-" + paymentId, "ORD-MP-500");
    }

    @Test
    @DisplayName("refundOrderPayment: si el pago ya estaba REFUND_PENDING no vuelve a invocar a Mercado Pago")
    void skipsGatewayCallWhenAlreadyRefundPending() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).build();
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .order(order)
                .status(PaymentStatus.REFUND_PENDING)
                .build();

        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(payment));

        refundService.refundOrderPayment(order, "Motivo");

        verify(mercadoPagoClient, never()).refundOrder(any(), any(), any());
    }
}
