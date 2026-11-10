package lat.nexofood.api.modules.payment.infrastructure.mercadopago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lat.nexofood.api.common.exception.PaymentGatewayException;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpAuthorizedPaymentResponse;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOAuthTokenResponse;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderRequest;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderResponse;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpPreapprovalRequest;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpPreapprovalResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class RestClientMercadoPagoClient implements MercadoPagoClient {

    private static final String HEADER_IDEMPOTENCY_KEY = "X-Idempotency-Key";

    private final RestClient restClient;
    private final MercadoPagoProperties properties;
    private final ObjectMapper objectMapper;

    @Autowired
    public RestClientMercadoPagoClient(
            MercadoPagoProperties properties,
            ObjectMapper objectMapper
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));

        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getApiBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public RestClientMercadoPagoClient(
            RestClient.Builder restClientBuilder,
            MercadoPagoProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .baseUrl(properties.getApiBaseUrl())
                .build();
    }

    @Override
    public MpOrderResponse createOrder(String accessToken, String idempotencyKey, MpOrderRequest request) {
        requireAccessToken(accessToken, "el restaurante");
        try {
            String rawBody = restClient.post()
                    .uri("/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .header(HEADER_IDEMPOTENCY_KEY, idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            MpOrderResponse parsed = parseJson(rawBody, MpOrderResponse.class, "POST /v1/orders");
            return parsed.withRawJson(rawBody);
        } catch (RestClientResponseException ex) {
            throw translateHttpError("crear la orden en Mercado Pago", ex);
        } catch (RestClientException ex) {
            throw new PaymentGatewayException("Error de conexión al crear la orden en Mercado Pago", ex);
        }
    }

    @Override
    public MpOrderResponse getOrder(String accessToken, String mpOrderId) {
        requireAccessToken(accessToken, "el restaurante");
        try {
            String rawBody = restClient.get()
                    .uri("/v1/orders/{id}", mpOrderId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            MpOrderResponse parsed = parseJson(rawBody, MpOrderResponse.class, "GET /v1/orders/" + mpOrderId);
            return parsed.withRawJson(rawBody);
        } catch (RestClientResponseException ex) {
            throw translateHttpError("consultar la orden en Mercado Pago", ex);
        } catch (RestClientException ex) {
            throw new PaymentGatewayException("Error de conexión al consultar la orden en Mercado Pago", ex);
        }
    }

    @Override
    public MpOrderResponse refundOrder(String accessToken, String idempotencyKey, String mpOrderId) {
        requireAccessToken(accessToken, "el restaurante");
        try {
            String rawBody = restClient.post()
                    .uri("/v1/orders/{id}/refund", mpOrderId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .header(HEADER_IDEMPOTENCY_KEY, idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .body(String.class);

            MpOrderResponse parsed = parseJson(rawBody, MpOrderResponse.class, "POST /v1/orders/" + mpOrderId + "/refund");
            return parsed.withRawJson(rawBody);
        } catch (RestClientResponseException ex) {
            throw translateHttpError("reembolsar la orden en Mercado Pago", ex);
        } catch (RestClientException ex) {
            throw new PaymentGatewayException("Error de conexión al reembolsar la orden en Mercado Pago", ex);
        }
    }

    @Override
    public MpOrderResponse cancelOrder(String accessToken, String idempotencyKey, String mpOrderId) {
        requireAccessToken(accessToken, "el restaurante");
        try {
            String rawBody = restClient.post()
                    .uri("/v1/orders/{id}/cancel", mpOrderId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .header(HEADER_IDEMPOTENCY_KEY, idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            MpOrderResponse parsed = parseJson(rawBody, MpOrderResponse.class, "POST /v1/orders/" + mpOrderId + "/cancel");
            return parsed.withRawJson(rawBody);
        } catch (RestClientResponseException ex) {
            throw translateHttpError("cancelar la orden en Mercado Pago", ex);
        } catch (RestClientException ex) {
            throw new PaymentGatewayException("Error de conexión al cancelar la orden en Mercado Pago", ex);
        }
    }

    @Override
    public MpPreapprovalResponse createPreapproval(MpPreapprovalRequest request) {
        String platformToken = requirePlatformToken();
        try {
            String rawBody = restClient.post()
                    .uri("/preapproval")
                    .header(HttpHeaders.AUTHORIZATION, bearer(platformToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            return parseJson(rawBody, MpPreapprovalResponse.class, "POST /preapproval");
        } catch (RestClientResponseException ex) {
            throw translateHttpError("crear la suscripción preaprobada en Mercado Pago", ex);
        } catch (RestClientException ex) {
            throw new PaymentGatewayException("Error de conexión al crear la suscripción en Mercado Pago", ex);
        }
    }

    @Override
    public MpPreapprovalResponse getPreapproval(String preapprovalId) {
        String platformToken = requirePlatformToken();
        try {
            String rawBody = restClient.get()
                    .uri("/preapproval/{id}", preapprovalId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(platformToken))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            return parseJson(rawBody, MpPreapprovalResponse.class, "GET /preapproval/" + preapprovalId);
        } catch (RestClientResponseException ex) {
            throw translateHttpError("consultar la suscripción en Mercado Pago", ex);
        } catch (RestClientException ex) {
            throw new PaymentGatewayException("Error de conexión al consultar la suscripción en Mercado Pago", ex);
        }
    }

    @Override
    public MpPreapprovalResponse updatePreapprovalStatus(String preapprovalId, String status) {
        String platformToken = requirePlatformToken();
        try {
            String rawBody = restClient.put()
                    .uri("/preapproval/{id}", preapprovalId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(platformToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("status", status))
                    .retrieve()
                    .body(String.class);

            return parseJson(rawBody, MpPreapprovalResponse.class, "PUT /preapproval/" + preapprovalId);
        } catch (RestClientResponseException ex) {
            throw translateHttpError("actualizar la suscripción en Mercado Pago", ex);
        } catch (RestClientException ex) {
            throw new PaymentGatewayException("Error de conexión al actualizar la suscripción en Mercado Pago", ex);
        }
    }

    @Override
    public MpAuthorizedPaymentResponse getAuthorizedPayment(String authorizedPaymentId) {
        String platformToken = requirePlatformToken();
        try {
            String rawBody = restClient.get()
                    .uri("/authorized_payments/{id}", authorizedPaymentId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(platformToken))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            MpAuthorizedPaymentResponse parsed = parseJson(
                    rawBody,
                    MpAuthorizedPaymentResponse.class,
                    "GET /authorized_payments/" + authorizedPaymentId
            );
            return parsed.withRawJson(rawBody);
        } catch (RestClientResponseException ex) {
            throw translateHttpError("consultar el cobro recurrente en Mercado Pago", ex);
        } catch (RestClientException ex) {
            throw new PaymentGatewayException("Error de conexión al consultar el cobro recurrente en Mercado Pago", ex);
        }
    }

    @Override
    public MpOAuthTokenResponse exchangeAuthorizationCode(String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", properties.getClientId());
        body.put("client_secret", properties.getClientSecret());
        body.put("grant_type", "authorization_code");
        body.put("code", code);
        body.put("redirect_uri", properties.getOauth().getRedirectUri());

        return executeOAuthTokenRequest(body, "canjear el código OAuth con Mercado Pago");
    }

    @Override
    public MpOAuthTokenResponse refreshToken(String refreshToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", properties.getClientId());
        body.put("client_secret", properties.getClientSecret());
        body.put("grant_type", "refresh_token");
        body.put("refresh_token", refreshToken);

        return executeOAuthTokenRequest(body, "renovar las credenciales OAuth de Mercado Pago");
    }

    private MpOAuthTokenResponse executeOAuthTokenRequest(Map<String, Object> body, String actionDescription) {
        try {
            String rawBody = restClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseJson(rawBody, MpOAuthTokenResponse.class, "POST /oauth/token");
        } catch (RestClientResponseException ex) {
            throw translateHttpError(actionDescription, ex);
        } catch (RestClientException ex) {
            throw new PaymentGatewayException("Error de conexión al " + actionDescription, ex);
        }
    }

    private <T> T parseJson(String rawBody, Class<T> targetClass, String operation) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new PaymentGatewayException("Respuesta vacía de Mercado Pago en " + operation);
        }
        try {
            return objectMapper.readValue(rawBody, targetClass);
        } catch (Exception ex) {
            throw new PaymentGatewayException("Error al procesar respuesta de Mercado Pago en " + operation, ex);
        }
    }

    private PaymentGatewayException translateHttpError(String actionDescription, RestClientResponseException ex) {
        int statusCode = ex.getStatusCode().value();
        String mpDetail = extractGatewayErrorMessage(ex.getResponseBodyAsString());
        log.warn("Mercado Pago respondió con HTTP {} al {}: {}", statusCode, actionDescription, mpDetail);
        String message = "Error al " + actionDescription
                + (mpDetail != null && !mpDetail.isBlank() ? ": " + mpDetail : " (código " + statusCode + ")");
        return new PaymentGatewayException(message, statusCode, ex);
    }

    private String extractGatewayErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("message")) {
                return root.get("message").asText();
            }
            if (root.has("errors") && root.get("errors").isArray() && !root.get("errors").isEmpty()) {
                JsonNode firstError = root.get("errors").get(0);
                String baseMessage = firstError.hasNonNull("message") ? firstError.get("message").asText() : null;
                if (firstError.has("details") && firstError.get("details").isArray() && !firstError.get("details").isEmpty()) {
                    String detail = firstError.get("details").get(0).asText();
                    return baseMessage != null ? baseMessage + " (" + detail + ")" : detail;
                }
                if (baseMessage != null) {
                    return baseMessage;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void requireAccessToken(String accessToken, String ownerLabel) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new PaymentGatewayException("Credenciales de Mercado Pago no configuradas para " + ownerLabel);
        }
    }

    private String requirePlatformToken() {
        String token = properties.getPlatformAccessToken();
        requireAccessToken(token, "la plataforma NexoFood");
        return token;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
