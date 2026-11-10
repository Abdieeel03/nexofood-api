package lat.nexofood.api.modules.payment.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lat.nexofood.api.common.exception.GlobalExceptionHandler;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.modules.payment.application.service.MercadoPagoOAuthService;
import lat.nexofood.api.modules.payment.application.service.MercadoPagoWebhookService;
import lat.nexofood.api.modules.payment.application.service.OrderPaymentService;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoProperties;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoWebhookSignatureValidator;
import lat.nexofood.api.modules.payment.web.dto.response.MercadoPagoConnectResponse;
import lat.nexofood.api.modules.payment.web.dto.response.PaymentStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
class PaymentAuthorizationControllerTest {

    private static final String WEBHOOK_SECRET = "webhook-secret-test-key";

    private MercadoPagoOAuthService oauthService;
    private MercadoPagoWebhookService webhookService;
    private OrderPaymentService orderPaymentService;
    private TenantAccessService tenantAccessService;
    private MercadoPagoWebhookSignatureValidator signatureValidator;

    private MercadoPagoConnectionController proxiedConnectionController;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        oauthService = mock(MercadoPagoOAuthService.class);
        webhookService = mock(MercadoPagoWebhookService.class);
        orderPaymentService = mock(OrderPaymentService.class);
        tenantAccessService = mock(TenantAccessService.class);

        signatureValidator = new MercadoPagoWebhookSignatureValidator(
                MercadoPagoProperties.builder().webhookSecret(WEBHOOK_SECRET).build()
        );

        StaticApplicationContext appContext = new StaticApplicationContext();
        appContext.getBeanFactory().registerSingleton("tenantAccess", tenantAccessService);
        appContext.refresh();

        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setApplicationContext(appContext);

        PreAuthorizeAuthorizationManager authorizationManager = new PreAuthorizeAuthorizationManager();
        authorizationManager.setExpressionHandler(expressionHandler);

        AuthorizationManagerBeforeMethodInterceptor interceptor =
                AuthorizationManagerBeforeMethodInterceptor.preAuthorize(authorizationManager);

        proxiedConnectionController = createSecuredProxy(
                new MercadoPagoConnectionController(oauthService),
                interceptor
        );

        mockMvc = MockMvcBuilders.standaloneSetup(
                        proxiedConnectionController,
                        new MercadoPagoOAuthCallbackController(oauthService),
                        new MercadoPagoWebhookController(signatureValidator, webhookService),
                        new StorefrontPaymentController(orderPaymentService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T> T createSecuredProxy(T target, AuthorizationManagerBeforeMethodInterceptor interceptor) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return (T) factory.getProxy();
    }

    @Test
    @DisplayName("MercadoPagoConnectionController: exige rol OWNER para /connect y /disconnect")
    void connectionControllerRequiresOwnerRole() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(tenantAccessService.isOwner(tenantId)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> proxiedConnectionController.getConnectUrl(tenantId));
        assertThrows(AccessDeniedException.class, () -> proxiedConnectionController.disconnect(tenantId));

        when(tenantAccessService.isOwner(tenantId)).thenReturn(true);
        when(oauthService.buildAuthorizationUrl(tenantId)).thenReturn(
                MercadoPagoConnectResponse.builder()
                        .authorizationUrl("https://auth.mercadopago.com.pe/authorization?client_id=123")
                        .build()
        );

        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/mercadopago/connect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorizationUrl").exists());
    }

    @Test
    @DisplayName("MercadoPagoWebhookController: devuelve 401 si la firma x-signature es inválida y 200 OK si es válida")
    void webhookControllerValidatesSignature() throws Exception {
        String dataId = "ORD-MP-900";
        String requestId = "req-abc";
        String ts = "1704908010";
        String body = """
                {
                  "type": "order",
                  "action": "order.processed",
                  "data": { "id": "ORD-MP-900" }
                }
                """;

        // 1. Firma inválida -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                        .queryParam("data.id", dataId)
                        .queryParam("type", "order")
                        .header("x-signature", "ts=" + ts + ",v1=bad_signature")
                        .header("x-request-id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(webhookService, never()).handleNotification("order", dataId);

        // 2. Firma válida -> 200 OK e invoca webhookService
        String validHmac = signatureValidator.computeHmacSha256Hex(
                signatureValidator.buildManifest(dataId, requestId, ts),
                WEBHOOK_SECRET
        );

        mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                        .queryParam("data.id", dataId)
                        .queryParam("type", "order")
                        .header("x-signature", "ts=" + ts + ",v1=" + validHmac)
                        .header("x-request-id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(webhookService).handleNotification("order", dataId);
    }

    @Test
    @DisplayName("OAuth callback redirige con 302 y StorefrontPaymentController devuelve estado del pago con orderId")
    void oauthCallbackRedirectsAndStorefrontPaymentStatusWorks() throws Exception {
        when(oauthService.handleCallback("code-1", "state-1", null))
                .thenReturn("http://localhost:5173/dashboard/settings/payments?mp=connected");

        mockMvc.perform(get("/api/v1/mercadopago/oauth/callback")
                        .queryParam("code", "code-1")
                        .queryParam("state", "state-1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:5173/dashboard/settings/payments?mp=connected"));

        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderPaymentService.getCustomerPaymentStatus("burger-bros", paymentId)).thenReturn(
                PaymentStatusResponse.builder()
                        .paymentId(paymentId)
                        .status(PaymentStatus.APPROVED)
                        .orderId(orderId)
                        .orderNumber("ORD-000001")
                        .amount(new BigDecimal("65.50"))
                        .currency("PEN")
                        .build()
        );

        mockMvc.perform(get("/api/v1/store/burger-bros/payments/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()));
    }
}
