package lat.nexofood.api.common.exception;

import lat.nexofood.api.common.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 409, 422})
    @DisplayName("Debe devolver 422 cuando la pasarela rechaza la solicitud con 4xx de validación/negocio")
    void shouldReturn422ForGatewayClientRejections(int gatewayStatus) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/subscriptions/checkout");
        PaymentGatewayException ex = new PaymentGatewayException(
                "Error al crear la suscripción preaprobada en Mercado Pago: Cannot pay an amount lower than S/ 2.00",
                gatewayStatus
        );

        ResponseEntity<ErrorResponse> response = handler.handlePaymentGatewayException(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(422);
        assertThat(response.getBody().getMessage()).contains("Cannot pay an amount lower than S/ 2.00");
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 502, 503})
    @DisplayName("Debe devolver 502 cuando la pasarela falla por credenciales, rate limit o error 5xx")
    void shouldReturn502ForGatewayAuthRateLimitOrServerErrors(int gatewayStatus) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/subscriptions/checkout");
        PaymentGatewayException ex = new PaymentGatewayException("Gateway failure", gatewayStatus);

        ResponseEntity<ErrorResponse> response = handler.handlePaymentGatewayException(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(502);
    }

    @Test
    @DisplayName("Debe devolver 502 cuando PaymentGatewayException no tiene código HTTP de pasarela (error de red)")
    void shouldReturn502WhenGatewayStatusIsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/subscriptions/checkout");
        PaymentGatewayException ex = new PaymentGatewayException("Error de conexión al crear la suscripción en Mercado Pago");

        ResponseEntity<ErrorResponse> response = handler.handlePaymentGatewayException(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Error de conexión al crear la suscripción en Mercado Pago");
    }
}
