package lat.nexofood.api.modules.payment.infrastructure.mercadopago;

import com.fasterxml.jackson.databind.ObjectMapper;
import lat.nexofood.api.common.exception.PaymentGatewayException;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderRequest;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderRequest.MpItem;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderRequest.MpPayer;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderResponse;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpPreapprovalRequest;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpPreapprovalRequest.MpAutoRecurring;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpPreapprovalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientMercadoPagoClientTest {

    private MockRestServiceServer mockServer;
    private RestClientMercadoPagoClient mpClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        MercadoPagoProperties properties = MercadoPagoProperties.builder()
                .apiBaseUrl("https://api.mercadopago.com")
                .clientId("client-123")
                .clientSecret("secret-456")
                .platformAccessToken("APP_USR-platform-token")
                .build();

        mpClient = new RestClientMercadoPagoClient(builder, properties, new ObjectMapper());
    }

    @Test
    @DisplayName("createOrder: envía POST /v1/orders con Bearer del tenant, X-Idempotency-Key y snake_case, y parsea checkout_url")
    void createOrderSerializesSnakeCaseAndParsesResponse() {
        String responseJson = """
                {
                  "id": "ORDTST01KS5AJ6",
                  "type": "online",
                  "processing_mode": "manual",
                  "status": "created",
                  "status_detail": "created",
                  "external_reference": "pay-001",
                  "total_amount": "65.50",
                  "total_paid_amount": "0.00",
                  "checkout_url": "https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=abc"
                }
                """;

        mockServer.expect(requestTo("https://api.mercadopago.com/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer APP_USR-tenant-token"))
                .andExpect(header("X-Idempotency-Key", "pay-001"))
                .andExpect(jsonPath("$.type").value("online"))
                .andExpect(jsonPath("$.processing_mode").value("manual"))
                .andExpect(jsonPath("$.total_amount").value("65.50"))
                .andExpect(jsonPath("$.external_reference").value("pay-001"))
                .andExpect(jsonPath("$.items[0].unit_price").value("65.50"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        MpOrderRequest request = MpOrderRequest.builder()
                .type("online")
                .processingMode("manual")
                .totalAmount("65.50")
                .externalReference("pay-001")
                .payer(MpPayer.builder().email("buyer@nexofood.lat").build())
                .items(List.of(MpItem.builder().title("1x Burger").unitPrice("65.50").quantity(1).build()))
                .build();

        MpOrderResponse response = mpClient.createOrder("APP_USR-tenant-token", "pay-001", request);

        assertEquals("ORDTST01KS5AJ6", response.id());
        assertEquals("https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=abc", response.checkoutUrl());
        assertEquals(new BigDecimal("65.50"), response.totalAmount());
        assertNotNull(response.rawJson());
        mockServer.verify();
    }

    @Test
    @DisplayName("createPreapproval y errores 4xx/5xx: usa token de plataforma y traduce errores a PaymentGatewayException")
    void createPreapprovalAndTranslatesGatewayErrors() {
        String preapprovalJson = """
                {
                  "id": "preapp-999",
                  "status": "pending",
                  "external_reference": "sub-123",
                  "init_point": "https://www.mercadopago.com.pe/subscriptions/checkout?preapproval_id=preapp-999"
                }
                """;

        mockServer.expect(requestTo("https://api.mercadopago.com/preapproval"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer APP_USR-platform-token"))
                .andExpect(jsonPath("$.auto_recurring.currency_id").value("PEN"))
                .andRespond(withSuccess(preapprovalJson, MediaType.APPLICATION_JSON));

        MpPreapprovalResponse preapproval = mpClient.createPreapproval(
                MpPreapprovalRequest.builder()
                        .reason("Plan Pro")
                        .externalReference("sub-123")
                        .payerEmail("owner@nexofood.lat")
                        .status("pending")
                        .autoRecurring(MpAutoRecurring.builder()
                                .frequency(1)
                                .frequencyType("months")
                                .transactionAmount(new BigDecimal("49.90"))
                                .currencyId("PEN")
                                .build())
                        .build()
        );

        assertEquals("preapp-999", preapproval.id());
        assertEquals("https://www.mercadopago.com.pe/subscriptions/checkout?preapproval_id=preapp-999", preapproval.initPoint());
        mockServer.verify();
        mockServer.reset();

        // Simular error 400 de Mercado Pago al reembolsar
        mockServer.expect(requestTo("https://api.mercadopago.com/v1/orders/ORD-ERR/refund"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Order cannot be refunded\"}"));

        PaymentGatewayException ex = assertThrows(PaymentGatewayException.class, () ->
                mpClient.refundOrder("APP_USR-tenant-token", "refund-1", "ORD-ERR")
        );
        assertEquals(400, ex.getGatewayStatus());
        assertTrue(ex.getMessage().contains("Order cannot be refunded"));
        mockServer.verify();
    }
}
