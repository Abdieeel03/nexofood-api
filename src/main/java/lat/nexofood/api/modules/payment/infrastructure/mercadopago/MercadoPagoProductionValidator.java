package lat.nexofood.api.modules.payment.infrastructure.mercadopago;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Valida en el arranque del perfil {@code prod} que no existan secretos débiles,
 * de ejemplo, de sandbox o URLs locales antes de aceptar tráfico real.
 */
@Component
@Profile("prod")
public class MercadoPagoProductionValidator implements InitializingBean {

    private static final Set<String> KNOWN_EXAMPLE_KEYS = Set.of(
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
            "70337336763979244226452948404D635166546A576E5A723475377821412544",
            "586E3272357538782F413F4428472B4B6250645367566B5970",
            "586E3272357538782F413F4428472B4B6250645367566B597033733676397924",
            "APP_USR-tu_access_token_de_plataforma"
    );

    private final MercadoPagoProperties properties;
    private final FrontendProperties frontendProperties;
    private final String cryptoMasterKey;
    private final String jwtSecret;
    private final String jwtRefreshSecret;

    public MercadoPagoProductionValidator(
            MercadoPagoProperties properties,
            FrontendProperties frontendProperties,
            @Value("${nexofood.crypto.master-key:}") String cryptoMasterKey,
            @Value("${app.jwt.secret:}") String jwtSecret,
            @Value("${app.jwt.refresh-secret:}") String jwtRefreshSecret
    ) {
        this.properties = properties;
        this.frontendProperties = frontendProperties;
        this.cryptoMasterKey = cryptoMasterKey;
        this.jwtSecret = jwtSecret;
        this.jwtRefreshSecret = jwtRefreshSecret;
    }

    @Override
    public void afterPropertiesSet() {
        if (properties == null) {
            throw new IllegalStateException("mercadopago (MercadoPagoProperties) no puede ser nulo en producción");
        }
        if (frontendProperties == null) {
            throw new IllegalStateException("app.frontend (FrontendProperties) no puede ser nulo en producción");
        }

        List<String> errors = new ArrayList<>();

        if (properties.isAllowTestMode()) {
            errors.add("mercadopago.allow-test-mode / MP_ALLOW_TEST_MODE debe ser false en producción");
        }

        if (properties.getTestPayerEmail() != null && !properties.getTestPayerEmail().isBlank()) {
            errors.add("mercadopago.test-payer-email / MP_TEST_PAYER_EMAIL debe estar vacío en producción");
        }

        MercadoPagoProperties.OAuth oauth = properties.getOauth();
        String stateSecret = oauth != null ? oauth.getStateSecret() : null;
        String redirectUri = oauth != null ? oauth.getRedirectUri() : null;

        validateSecret(errors, "mercadopago.client-id / MP_CLIENT_ID", properties.getClientId());
        validateSecret(errors, "mercadopago.client-secret / MP_CLIENT_SECRET", properties.getClientSecret());
        validateSecret(errors, "mercadopago.platform-access-token / MP_PLATFORM_ACCESS_TOKEN", properties.getPlatformAccessToken());
        validateSecret(errors, "mercadopago.webhook-secret / MP_WEBHOOK_SECRET", properties.getWebhookSecret());
        validateSecret(errors, "mercadopago.oauth.state-secret / MP_OAUTH_STATE_SECRET", stateSecret);
        validateSecret(errors, "nexofood.crypto.master-key / CRYPTO_MASTER_KEY", cryptoMasterKey);
        validateSecret(errors, "app.jwt.secret / JWT_SECRET", jwtSecret);
        validateSecret(errors, "app.jwt.refresh-secret / JWT_SECRET_REFRESH", jwtRefreshSecret);

        validateProductionHttpsUrl(errors, "mercadopago.oauth.redirect-uri / MP_OAUTH_REDIRECT_URI", redirectUri);
        validateProductionHttpsUrl(errors, "app.frontend.base-url / FRONTEND_URL", frontendProperties.getBaseUrl());

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Configuración de producción inválida: " + String.join("; ", errors)
            );
        }
    }

    private void validateSecret(List<String> errors, String variableName, String value) {
        if (value == null || value.isBlank()) {
            errors.add(variableName + " es obligatorio en producción");
            return;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("tu_") || trimmed.startsWith("TEST-")) {
            errors.add(variableName + " usa un valor de prueba o placeholder no permitido en producción");
            return;
        }
        if (KNOWN_EXAMPLE_KEYS.contains(trimmed)) {
            errors.add(variableName + " usa una clave de ejemplo conocida no permitida en producción");
        }
    }

    private void validateProductionHttpsUrl(List<String> errors, String variableName, String url) {
        if (url == null || url.isBlank()) {
            errors.add(variableName + " es obligatorio en producción");
            return;
        }
        String normalized = url.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("https://")) {
            errors.add(variableName + " debe comenzar con https:// en producción");
            return;
        }
        if (normalized.contains("localhost") || normalized.contains("127.0.0.1")) {
            errors.add(variableName + " no puede apuntar a localhost ni 127.0.0.1 en producción");
        }
    }
}
