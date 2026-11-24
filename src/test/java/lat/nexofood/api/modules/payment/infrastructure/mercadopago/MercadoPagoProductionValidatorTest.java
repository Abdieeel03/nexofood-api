package lat.nexofood.api.modules.payment.infrastructure.mercadopago;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Profile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MercadoPagoProductionValidatorTest {

    private static final String VALID_CRYPTO_MASTER_KEY = "cHJvZC1hZXMtMjU2LWdjbS1tYXN0ZXIta2V5LTAxMjM=";
    private static final String VALID_JWT_SECRET = "prod-jwt-signing-secret-key-minimum-32-chars-01";
    private static final String VALID_JWT_REFRESH_SECRET = "prod-jwt-refresh-secret-key-minimum-32-chars-02";

    private MercadoPagoProperties validMpProperties() {
        return MercadoPagoProperties.builder()
                .allowTestMode(false)
                .testPayerEmail("")
                .clientId("1234567890123456")
                .clientSecret("prod-mp-client-secret-value-01")
                .platformAccessToken("APP_USR-9999999999999999-010101-abcdef1234567890abcdef1234567890-123456789")
                .webhookSecret("prod-webhook-hmac-secret-value-01")
                .oauth(MercadoPagoProperties.OAuth.builder()
                        .redirectUri("https://api.nexofood-app.com/api/v1/mercadopago/oauth/callback")
                        .stateSecret("prod-oauth-state-signing-key-minimum-32-bytes-01")
                        .build())
                .build();
    }

    private FrontendProperties validFrontendProperties() {
        return FrontendProperties.builder()
                .baseUrl("https://nexofood-app.com")
                .build();
    }

    private MercadoPagoProductionValidator createValidator(
            MercadoPagoProperties mpProperties,
            FrontendProperties frontendProperties,
            String cryptoMasterKey,
            String jwtSecret,
            String jwtRefreshSecret
    ) {
        return new MercadoPagoProductionValidator(
                mpProperties,
                frontendProperties,
                cryptoMasterKey,
                jwtSecret,
                jwtRefreshSecret
        );
    }

    @Test
    @DisplayName("afterPropertiesSet: acepta configuración válida de producción y está restringido al perfil prod")
    void acceptsValidProductionConfiguration() {
        Profile profile = MercadoPagoProductionValidator.class.getAnnotation(Profile.class);
        assertNotNull(profile);
        assertArrayEquals(new String[]{"prod"}, profile.value());

        MercadoPagoProductionValidator validator = createValidator(
                validMpProperties(),
                validFrontendProperties(),
                VALID_CRYPTO_MASTER_KEY,
                VALID_JWT_SECRET,
                VALID_JWT_REFRESH_SECRET
        );

        assertDoesNotThrow(validator::afterPropertiesSet);
    }

    @Test
    @DisplayName("afterPropertiesSet: rechaza allowTestMode=true en producción")
    void rejectsAllowTestModeTrue() {
        MercadoPagoProperties props = validMpProperties();
        props.setAllowTestMode(true);

        MercadoPagoProductionValidator validator = createValidator(
                props, validFrontendProperties(), VALID_CRYPTO_MASTER_KEY, VALID_JWT_SECRET, VALID_JWT_REFRESH_SECRET
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
        assertTrue(ex.getMessage().contains("MP_ALLOW_TEST_MODE"));
    }

    @Test
    @DisplayName("afterPropertiesSet: rechaza testPayerEmail no vacío en producción")
    void rejectsNonEmptyTestPayerEmail() {
        MercadoPagoProperties props = validMpProperties();
        props.setTestPayerEmail("test_user_comprador@testuser.com");

        MercadoPagoProductionValidator validator = createValidator(
                props, validFrontendProperties(), VALID_CRYPTO_MASTER_KEY, VALID_JWT_SECRET, VALID_JWT_REFRESH_SECRET
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
        assertTrue(ex.getMessage().contains("MP_TEST_PAYER_EMAIL"));
        assertFalse(ex.getMessage().contains("test_user_comprador@testuser.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
            "70337336763979244226452948404D635166546A576E5A723475377821412544",
            "586E3272357538782F413F4428472B4B6250645367566B5970",
            "586E3272357538782F413F4428472B4B6250645367566B597033733676397924"
    })
    @DisplayName("afterPropertiesSet: rechaza claves de ejemplo conocidas sin filtrar su valor en el mensaje")
    void rejectsKnownExampleKeysWithoutLeakingSecretValue(String exampleKey) {
        MercadoPagoProperties props = validMpProperties();
        props.getOauth().setStateSecret(exampleKey);

        MercadoPagoProductionValidator validatorState = createValidator(
                props, validFrontendProperties(), VALID_CRYPTO_MASTER_KEY, VALID_JWT_SECRET, VALID_JWT_REFRESH_SECRET
        );
        IllegalStateException stateEx = assertThrows(IllegalStateException.class, validatorState::afterPropertiesSet);
        assertTrue(stateEx.getMessage().contains("MP_OAUTH_STATE_SECRET"));
        assertFalse(stateEx.getMessage().contains(exampleKey));

        MercadoPagoProductionValidator validatorCrypto = createValidator(
                validMpProperties(), validFrontendProperties(), exampleKey, VALID_JWT_SECRET, VALID_JWT_REFRESH_SECRET
        );
        IllegalStateException cryptoEx = assertThrows(IllegalStateException.class, validatorCrypto::afterPropertiesSet);
        assertTrue(cryptoEx.getMessage().contains("CRYPTO_MASTER_KEY"));
        assertFalse(cryptoEx.getMessage().contains(exampleKey));

        MercadoPagoProductionValidator validatorJwt = createValidator(
                validMpProperties(), validFrontendProperties(), VALID_CRYPTO_MASTER_KEY, exampleKey, VALID_JWT_REFRESH_SECRET
        );
        IllegalStateException jwtEx = assertThrows(IllegalStateException.class, validatorJwt::afterPropertiesSet);
        assertTrue(jwtEx.getMessage().contains("JWT_SECRET"));
        assertFalse(jwtEx.getMessage().contains(exampleKey));

        MercadoPagoProductionValidator validatorRefresh = createValidator(
                validMpProperties(), validFrontendProperties(), VALID_CRYPTO_MASTER_KEY, VALID_JWT_SECRET, exampleKey
        );
        IllegalStateException refreshEx = assertThrows(IllegalStateException.class, validatorRefresh::afterPropertiesSet);
        assertTrue(refreshEx.getMessage().contains("JWT_SECRET_REFRESH"));
        assertFalse(refreshEx.getMessage().contains(exampleKey));
    }

    @Test
    @DisplayName("afterPropertiesSet: rechaza prefijos TEST- y tu_ en credenciales y secretos sin exponer el secreto")
    void rejectsTestAndPlaceholderPrefixesWithoutLeakingValues() {
        String testPrefixedToken = "TEST-1234567890-abcdef-987654";
        MercadoPagoProperties propsWithTestToken = validMpProperties();
        propsWithTestToken.setPlatformAccessToken(testPrefixedToken);

        IllegalStateException tokenEx = assertThrows(IllegalStateException.class, () -> createValidator(
                propsWithTestToken, validFrontendProperties(), VALID_CRYPTO_MASTER_KEY, VALID_JWT_SECRET, VALID_JWT_REFRESH_SECRET
        ).afterPropertiesSet());
        assertTrue(tokenEx.getMessage().contains("MP_PLATFORM_ACCESS_TOKEN"));
        assertFalse(tokenEx.getMessage().contains(testPrefixedToken));

        String tuPrefixedSecret = "tu_mp_client_secret";
        MercadoPagoProperties propsWithTuSecret = validMpProperties();
        propsWithTuSecret.setClientSecret(tuPrefixedSecret);

        IllegalStateException secretEx = assertThrows(IllegalStateException.class, () -> createValidator(
                propsWithTuSecret, validFrontendProperties(), VALID_CRYPTO_MASTER_KEY, VALID_JWT_SECRET, VALID_JWT_REFRESH_SECRET
        ).afterPropertiesSet());
        assertTrue(secretEx.getMessage().contains("MP_CLIENT_SECRET"));
        assertFalse(secretEx.getMessage().contains(tuPrefixedSecret));
    }

    @Test
    @DisplayName("afterPropertiesSet: rechaza secretos o credenciales nulos/vacíos")
    void rejectsMissingOrBlankSecrets() {
        List<String> blankValues = List.of("", "   ");

        for (String blank : blankValues) {
            MercadoPagoProperties noClientId = validMpProperties();
            noClientId.setClientId(blank);
            assertTrue(assertThrows(IllegalStateException.class, () -> createValidator(
                    noClientId, validFrontendProperties(), VALID_CRYPTO_MASTER_KEY, VALID_JWT_SECRET, VALID_JWT_REFRESH_SECRET
            ).afterPropertiesSet()).getMessage().contains("MP_CLIENT_ID"));

            MercadoPagoProperties noClientSecret = validMpProperties();
            noClientSecret.setClientSecret(blank);
            assertTrue(assertThrows(IllegalStateException.class, () -> createValidator(
                    noClientSecret, validFrontendProperties(), VALID_CRYPTO_MASTER_KEY, VALID_JWT_SECRET, VALID_JWT_REFRESH_SECRET
            ).afterPropertiesSet()).getMessage().contains("MP_CLIENT_SECRET"));

            MercadoPagoProperties noPlatformToken = validMpProperties();
            noPlatformToken.setPlatformAccessToken(blank);
            assertTrue(assertThrows(IllegalStateException.class, () -> createValidator(
                    noPlatformToken, validFrontendProperties(), VALID_CRYPTO_MASTER_KEY, VALID_JWT_SECRET, VALID_JWT_REFRESH_SECRET
            ).afterPropertiesSet()).getMessage().contains("MP_PLATFORM_ACCESS_TOKEN"));

            MercadoPagoProperties noWebhookSecret = validMpProperties();
            noWebhookSecret.setWebhookSecret(blank);
            assertTrue(assertThrows(IllegalStateException.class, () -> createValidator(
                    noWebhookSecret, validFrontendProperties(), VALID_CRYPTO_MASTER_KEY, VALID_JWT_SECRET, VALID_JWT_REFRESH_SECRET
            ).afterPropertiesSet()).getMessage().contains("MP_WEBHOOK_SECRET"));
        }

        MercadoPagoProperties nullOauth = validMpProperties();
        nullOauth.setOauth(null);
        IllegalStateException oauthEx = assertThrows(IllegalStateException.class, () -> createValidator(
                nullOauth, validFrontendProperties(), VALID_CRYPTO_MASTER_KEY, VALID_JWT_SECRET, VALID_JWT_REFRESH_SECRET
        ).afterPropertiesSet());
        assertTrue(oauthEx.getMessage().contains("MP_OAUTH_STATE_SECRET"));
        assertTrue(oauthEx.getMessage().contains("MP_OAUTH_REDIRECT_URI"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "http://api.nexofood-app.com/api/v1/mercadopago/oauth/callback",
            "https://LocalHost:8080/api/v1/mercadopago/oauth/callback",
            "https://127.0.0.1:8080/api/v1/mercadopago/oauth/callback"
    })
    @DisplayName("afterPropertiesSet: rechaza oauth.redirectUri vacío, sin https:// o con localhost/127.0.0.1")
    void rejectsInvalidRedirectUri(String badRedirectUri) {
        MercadoPagoProperties props = validMpProperties();
        props.getOauth().setRedirectUri(badRedirectUri);

        MercadoPagoProductionValidator validator = createValidator(
                props, validFrontendProperties(), VALID_CRYPTO_MASTER_KEY, VALID_JWT_SECRET, VALID_JWT_REFRESH_SECRET
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
        assertTrue(ex.getMessage().contains("MP_OAUTH_REDIRECT_URI"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "http://nexofood-app.com",
            "https://LOCALHOST:5173",
            "https://127.0.0.1:5173"
    })
    @DisplayName("afterPropertiesSet: rechaza frontend.baseUrl vacío, sin https:// o con localhost/127.0.0.1")
    void rejectsInvalidFrontendBaseUrl(String badBaseUrl) {
        FrontendProperties frontendProps = FrontendProperties.builder()
                .baseUrl(badBaseUrl)
                .build();

        MercadoPagoProductionValidator validator = createValidator(
                validMpProperties(), frontendProps, VALID_CRYPTO_MASTER_KEY, VALID_JWT_SECRET, VALID_JWT_REFRESH_SECRET
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
        assertTrue(ex.getMessage().contains("FRONTEND_URL"));
    }
}
