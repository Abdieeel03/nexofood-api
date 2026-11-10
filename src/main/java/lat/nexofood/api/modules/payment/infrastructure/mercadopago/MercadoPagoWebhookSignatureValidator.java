package lat.nexofood.api.modules.payment.infrastructure.mercadopago;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Valida la firma criptográfica HMAC-SHA256 de los webhooks de Mercado Pago (`x-signature`).
 * <p>
 * Formato de cabecera: {@code x-signature: ts=1704908010,v1=618c85345248dd820d5fd456117c2ab2...}
 * <p>
 * Plantilla de manifiesto oficial de Mercado Pago:
 * {@code id:[data.id_url];request-id:[x-request-id_header];ts:[ts_header];}
 * (Si alguno de los valores no está presente, se omite ese bloque clave:valor).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MercadoPagoWebhookSignatureValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final MercadoPagoProperties properties;

    public boolean isValid(String xSignatureHeader, String xRequestIdHeader, String dataId) {
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("No se ha configurado mercadopago.webhook-secret; se rechaza el webhook por seguridad");
            return false;
        }
        if (xSignatureHeader == null || xSignatureHeader.isBlank()) {
            return false;
        }

        String ts = null;
        String v1 = null;

        String[] parts = xSignatureHeader.split(",");
        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String value = kv[1].trim();
                if ("ts".equals(key)) {
                    ts = value;
                } else if ("v1".equals(key)) {
                    v1 = value;
                }
            }
        }

        if (ts == null || ts.isBlank() || v1 == null || v1.isBlank()) {
            return false;
        }

        String manifest = buildManifest(dataId, xRequestIdHeader, ts);
        String expectedHex = computeHmacSha256Hex(manifest, secret);
        if (expectedHex == null) {
            return false;
        }

        byte[] expectedBytes = expectedHex.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, providedBytes);
    }

    public String buildManifest(String dataId, String xRequestId, String ts) {
        StringBuilder sb = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            // Según la documentación de Mercado Pago, si data.id es alfanumérico debe ir en minúsculas
            sb.append("id:").append(dataId.trim().toLowerCase(Locale.ROOT)).append(";");
        }
        if (xRequestId != null && !xRequestId.isBlank()) {
            sb.append("request-id:").append(xRequestId.trim()).append(";");
        }
        if (ts != null && !ts.isBlank()) {
            sb.append("ts:").append(ts.trim()).append(";");
        }
        return sb.toString();
    }

    public String computeHmacSha256Hex(String manifest, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKey);
            byte[] hash = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            log.error("Error calculando HMAC-SHA256 para webhook de Mercado Pago", ex);
            return null;
        }
    }
}
