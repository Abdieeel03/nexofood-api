package lat.nexofood.api.modules.payment.application.service;

import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.FrontendProperties;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoClient;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoProperties;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOAuthTokenResponse;
import lat.nexofood.api.modules.payment.web.dto.response.MercadoPagoConnectResponse;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MercadoPagoOAuthServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private MercadoPagoClient mercadoPagoClient;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private MercadoPagoOAuthServiceImpl oauthService;
    private Tenant tenant;
    private UUID userId;

    @BeforeEach
    void setUp() {
        MercadoPagoProperties properties = MercadoPagoProperties.builder()
                .authBaseUrl("https://auth.mercadopago.com.pe")
                .clientId("mp-client-123")
                .clientSecret("mp-secret-456")
                .build();
        oauthService = new MercadoPagoOAuthServiceImpl(
                tenantRepository,
                mercadoPagoClient,
                properties,
                new FrontendProperties(),
                currentUserProvider
        );

        userId = UUID.randomUUID();
        tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Pizzeria Napoli")
                .slug("pizzeria-napoli")
                .owner(User.builder().id(userId).build())
                .build();
    }

    @Test
    @DisplayName("buildAuthorizationUrl y handleCallback: genera state firmado, canjea code por tokens y los guarda en el Tenant")
    void buildsAuthUrlAndExchangesCodeOnCallback() {
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);

        MercadoPagoConnectResponse connectResp = oauthService.buildAuthorizationUrl(tenant.getId());
        assertTrue(connectResp.authorizationUrl().startsWith("https://auth.mercadopago.com.pe/authorization?"));
        assertTrue(connectResp.authorizationUrl().contains("client_id=mp-client-123"));

        String state = oauthService.generateStateToken(tenant.getId(), userId);
        when(mercadoPagoClient.exchangeAuthorizationCode("TG-code-123")).thenReturn(
                MpOAuthTokenResponse.builder()
                        .accessToken("APP_USR-new-access")
                        .refreshToken("TG-new-refresh")
                        .publicKey("APP_USR-pub-key")
                        .userId(445566L)
                        .expiresIn(15552000L)
                        .build()
        );

        String redirectUrl = oauthService.handleCallback("TG-code-123", state, null);

        assertTrue(redirectUrl.endsWith("/dashboard/settings/payments?mp=connected"));
        assertEquals("APP_USR-new-access", tenant.getMpAccessToken());
        assertEquals("TG-new-refresh", tenant.getMpRefreshToken());
        assertEquals("445566", tenant.getMpUserId());
        assertNotNull(tenant.getMpConnectedAt());
        assertNotNull(tenant.getMpTokenExpiresAt());
        verify(tenantRepository).save(tenant);
    }

    @Test
    @DisplayName("handleCallback con state alterado y disconnect: rechaza state inválido y limpia credenciales al desconectar")
    void rejectsInvalidStateAndClearsCredentialsOnDisconnect() {
        String errorUrl = oauthService.handleCallback("TG-code", "state-invalido-manipulado", null);
        assertTrue(errorUrl.contains("mp=error&reason=invalid_state"));

        tenant.setMpAccessToken("APP_USR-tok");
        tenant.setMpRefreshToken("TG-ref");
        tenant.setMpUserId("445566");
        tenant.setMpConnectedAt(OffsetDateTime.now());
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));

        oauthService.disconnect(tenant.getId());

        assertFalse(tenant.isMercadoPagoConnected());
        assertNull(tenant.getMpAccessToken());
        assertNull(tenant.getMpRefreshToken());
        assertNull(tenant.getMpUserId());
        verify(tenantRepository).save(tenant);
    }
}
