package lat.nexofood.api.modules.payment.infrastructure.mercadopago;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MercadoPagoWebhookSignatureValidatorTest {

    private static final String WEBHOOK_SECRET = "my-super-secret-webhook-key-12345";

    private MercadoPagoWebhookSignatureValidator validator;

    @BeforeEach
    void setUp() {
        MercadoPagoProperties properties = MercadoPagoProperties.builder()
                .webhookSecret(WEBHOOK_SECRET)
                .build();
        validator = new MercadoPagoWebhookSignatureValidator(properties);
    }

    @Test
    @DisplayName("isValid: acepta firma HMAC-SHA256 válida y convierte data.id alfanumérico a minúsculas")
    void validatesCorrectHmacSignatureAndLowercasesAlphanumericDataId() {
        String dataId = "ORDTST01KS5AJ6HTK2HRQ3XJ3C2JCKP9";
        String requestId = "9b2d152a-3568-41fb-a111-5426164f9f74";
        String ts = "1704908010";

        String manifest = validator.buildManifest(dataId, requestId, ts);
        assertEquals("id:ordtst01ks5aj6htk2hrq3xj3c2jckp9;request-id:9b2d152a-3568-41fb-a111-5426164f9f74;ts:1704908010;", manifest);

        String hmac = validator.computeHmacSha256Hex(manifest, WEBHOOK_SECRET);
        String xSignature = "ts=" + ts + ",v1=" + hmac;

        assertTrue(validator.isValid(xSignature, requestId, dataId));
    }

    @Test
    @DisplayName("isValid: rechaza firma alterada, ts ausente o webhookSecret no configurado")
    void rejectsTamperedSignatureOrMissingFields() {
        String dataId = "987654321";
        String requestId = "req-001";
        String ts = "1704908010";

        String validHmac = validator.computeHmacSha256Hex(
                validator.buildManifest(dataId, requestId, ts),
                WEBHOOK_SECRET
        );

        // Firma manipulada
        assertFalse(validator.isValid("ts=" + ts + ",v1=0000" + validHmac.substring(4), requestId, dataId));

        // Sin ts
        assertFalse(validator.isValid("v1=" + validHmac, requestId, dataId));

        // Header vacío
        assertFalse(validator.isValid("", requestId, dataId));

        // Sin secret configurado
        MercadoPagoWebhookSignatureValidator unconfiguredValidator = new MercadoPagoWebhookSignatureValidator(
                MercadoPagoProperties.builder().webhookSecret("").build()
        );
        assertFalse(unconfiguredValidator.isValid("ts=" + ts + ",v1=" + validHmac, requestId, dataId));
    }

    @Test
    @DisplayName("isValid: con allowTestMode=false y clientSecret vacío, NO activa bypass de modo prueba ante firma HMAC inválida")
    void rejectsInvalidHmacWhenAllowTestModeIsFalseEvenWithBlankClientSecretAndPlatformToken() {
        MercadoPagoProperties properties = MercadoPagoProperties.builder()
                .allowTestMode(false)
                .clientSecret("")
                .platformAccessToken("APP_USR-platform-test-token")
                .webhookSecret(WEBHOOK_SECRET)
                .build();
        MercadoPagoWebhookSignatureValidator strictValidator = new MercadoPagoWebhookSignatureValidator(properties);

        String dataId = "987654321";
        String requestId = "req-002";
        String ts = "1704908010";
        String validHmac = strictValidator.computeHmacSha256Hex(
                strictValidator.buildManifest(dataId, requestId, ts),
                WEBHOOK_SECRET
        );
        String tamperedSignature = "ts=" + ts + ",v1=0000" + validHmac.substring(4);

        assertFalse(strictValidator.isValid(tamperedSignature, requestId, dataId));
        assertTrue(strictValidator.isValid("ts=" + ts + ",v1=" + validHmac, requestId, dataId));
    }

    @Test
    @DisplayName("isValid: con allowTestMode=true, clientSecret vacío y platformAccessToken presente, activa bypass de modo prueba")
    void allowsBypassWhenAllowTestModeIsTrueWithMissingClientSecretAndPlatformToken() {
        MercadoPagoProperties devProperties = MercadoPagoProperties.builder()
                .allowTestMode(true)
                .clientSecret("")
                .platformAccessToken("APP_USR-platform-test-token")
                .webhookSecret(WEBHOOK_SECRET)
                .build();
        MercadoPagoWebhookSignatureValidator devValidator = new MercadoPagoWebhookSignatureValidator(devProperties);

        String dataId = "987654321";
        String requestId = "req-003";
        String ts = "1704908010";
        String validHmac = devValidator.computeHmacSha256Hex(
                devValidator.buildManifest(dataId, requestId, ts),
                WEBHOOK_SECRET
        );
        String mismatchedSignature = "ts=" + ts + ",v1=0000" + validHmac.substring(4);

        assertTrue(devValidator.isValid(mismatchedSignature, requestId, dataId));
    }
}
