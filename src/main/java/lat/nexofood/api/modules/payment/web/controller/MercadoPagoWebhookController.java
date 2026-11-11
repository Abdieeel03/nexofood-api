package lat.nexofood.api.modules.payment.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lat.nexofood.api.modules.payment.application.service.MercadoPagoWebhookService;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoWebhookSignatureValidator;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpWebhookNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/mercadopago")
@RequiredArgsConstructor
@Tag(name = "Payment - Mercado Pago Webhook", description = "Recepción verificada de notificaciones de órdenes y suscripciones desde Mercado Pago")
public class MercadoPagoWebhookController {

    private final MercadoPagoWebhookSignatureValidator signatureValidator;
    private final MercadoPagoWebhookService webhookService;

    @GetMapping
    @Operation(summary = "Health check y verificación de endpoint webhook para Mercado Pago")
    public ResponseEntity<Map<String, String>> verifyWebhookEndpoint() {
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Mercado Pago webhook endpoint active"));
    }

    @PostMapping
    @Operation(summary = "Recibir notificación Webhook de Mercado Pago (valida firma HMAC-SHA256 x-signature)")
    public ResponseEntity<Void> receiveWebhook(
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestParam(value = "data.id", required = false) String queryDataId,
            @RequestParam(value = "id", required = false) String queryId,
            @RequestParam(value = "type", required = false) String queryType,
            @RequestParam(value = "topic", required = false) String queryTopic,
            @RequestBody(required = false) MpWebhookNotification body
    ) {
        String resolvedDataId = resolveDataId(queryDataId, queryId, body);
        String resolvedType = resolveType(queryType, queryTopic, body);

        log.info("Notificación Webhook de Mercado Pago recibida (type={}, dataId={})", resolvedType, resolvedDataId);

        if (!signatureValidator.isValid(xSignature, xRequestId, resolvedDataId)) {
            log.warn("Firma x-signature inválida en webhook de Mercado Pago (type={}, dataId={})",
                    resolvedType, resolvedDataId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        webhookService.handleNotification(resolvedType, resolvedDataId);
        log.info("Notificación Webhook de Mercado Pago procesada con éxito (type={}, dataId={})", resolvedType, resolvedDataId);
        return ResponseEntity.ok().build();
    }

    private String resolveDataId(String queryDataId, String queryId, MpWebhookNotification body) {
        if (queryDataId != null && !queryDataId.isBlank()) {
            return queryDataId;
        }
        if (body != null && body.resolvedDataId() != null) {
            return body.resolvedDataId();
        }
        return queryId;
    }

    private String resolveType(String queryType, String queryTopic, MpWebhookNotification body) {
        if (queryType != null && !queryType.isBlank()) {
            return queryType;
        }
        if (body != null && body.resolvedType() != null) {
            return body.resolvedType();
        }
        return queryTopic;
    }
}
