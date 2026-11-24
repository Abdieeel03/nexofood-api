package lat.nexofood.api.modules.subscription.web.controller;

import lat.nexofood.api.common.exception.GlobalExceptionHandler;
import lat.nexofood.api.modules.subscription.application.service.subscription.SubscriptionBillingService;
import lat.nexofood.api.modules.subscription.application.service.subscription.SubscriptionService;
import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import lat.nexofood.api.modules.subscription.web.dto.request.SubscriptionCheckoutRequest;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionCheckoutResponse;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionPaymentConfigResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubscriptionControllerTest {

    private SubscriptionService subscriptionService;
    private SubscriptionBillingService subscriptionBillingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        subscriptionService = mock(SubscriptionService.class);
        subscriptionBillingService = mock(SubscriptionBillingService.class);

        JsonMapper strictJsonMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SubscriptionController(subscriptionService, subscriptionBillingService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(strictJsonMapper))
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/subscriptions/config: expone publicKey, currency y cardPaymentsEnabled")
    void getPaymentConfigReturnsPublicKeyConfig() throws Exception {
        when(subscriptionBillingService.getPaymentConfig()).thenReturn(
                SubscriptionPaymentConfigResponse.builder()
                        .publicKey("APP_USR-platform-public-key")
                        .currency("PEN")
                        .cardPaymentsEnabled(true)
                        .build()
        );

        mockMvc.perform(get("/api/v1/subscriptions/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.publicKey").value("APP_USR-platform-public-key"))
                .andExpect(jsonPath("$.data.currency").value("PEN"))
                .andExpect(jsonPath("$.data.cardPaymentsEnabled").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/subscriptions/checkout: acepta planId, cardTokenId y payerEmail y retorna ACTIVE sin initPoint")
    void checkoutSubscriptionDirectCardModeReturnsActive() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        when(subscriptionBillingService.startCheckout(any(SubscriptionCheckoutRequest.class))).thenReturn(
                SubscriptionCheckoutResponse.builder()
                        .subscriptionId(subscriptionId)
                        .planId(planId)
                        .mpPreapprovalId("preapp-card-999")
                        .initPoint(null)
                        .status(SubscriptionStatus.ACTIVE)
                        .mpPreapprovalStatus("authorized")
                        .build()
        );

        String payload = """
                {
                  "planId": "%s",
                  "cardTokenId": "tok_abc_123",
                  "payerEmail": "pagador@ejemplo.com"
                }
                """.formatted(planId);

        mockMvc.perform(post("/api/v1/subscriptions/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.subscriptionId").value(subscriptionId.toString()))
                .andExpect(jsonPath("$.data.planId").value(planId.toString()))
                .andExpect(jsonPath("$.data.mpPreapprovalId").value("preapp-card-999"))
                .andExpect(jsonPath("$.data.initPoint").value(nullValue()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.mpPreapprovalStatus").value("authorized"));

        verify(subscriptionBillingService).startCheckout(
                SubscriptionCheckoutRequest.builder()
                        .planId(planId)
                        .cardTokenId("tok_abc_123")
                        .payerEmail("pagador@ejemplo.com")
                        .build()
        );
    }

    @Test
    @DisplayName("POST /api/v1/subscriptions/checkout: mantiene compatibilidad con body solo planId (modo redirect)")
    void checkoutSubscriptionRedirectModeAcceptsPlanIdOnly() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        when(subscriptionBillingService.startCheckout(any(SubscriptionCheckoutRequest.class))).thenReturn(
                SubscriptionCheckoutResponse.builder()
                        .subscriptionId(subscriptionId)
                        .planId(planId)
                        .mpPreapprovalId("preapp-redirect-999")
                        .initPoint("https://www.mercadopago.com.pe/subscriptions/checkout?preapproval_id=preapp-redirect-999")
                        .status(SubscriptionStatus.PENDING)
                        .mpPreapprovalStatus("pending")
                        .build()
        );

        String payload = """
                {
                  "planId": "%s"
                }
                """.formatted(planId);

        mockMvc.perform(post("/api/v1/subscriptions/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.initPoint").value("https://www.mercadopago.com.pe/subscriptions/checkout?preapproval_id=preapp-redirect-999"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.mpPreapprovalStatus").value("pending"));

        verify(subscriptionBillingService).startCheckout(
                SubscriptionCheckoutRequest.builder()
                        .planId(planId)
                        .build()
        );
    }

    @Test
    @DisplayName("POST /api/v1/subscriptions/checkout: valida payerEmail inválido y rechaza propiedades desconocidas")
    void checkoutSubscriptionValidatesEmailAndRejectsUnknownProperties() throws Exception {
        UUID planId = UUID.randomUUID();

        String invalidEmailPayload = """
                {
                  "planId": "%s",
                  "payerEmail": "correo-no-valido"
                }
                """.formatted(planId);

        mockMvc.perform(post("/api/v1/subscriptions/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidEmailPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("El correo del pagador no es válido")));

        String unknownFieldPayload = """
                {
                  "planId": "%s",
                  "unexpectedField": "foo"
                }
                """.formatted(planId);

        mockMvc.perform(post("/api/v1/subscriptions/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownFieldPayload))
                .andExpect(status().isBadRequest());

        verify(subscriptionBillingService, never()).startCheckout(any(SubscriptionCheckoutRequest.class));
    }
}
