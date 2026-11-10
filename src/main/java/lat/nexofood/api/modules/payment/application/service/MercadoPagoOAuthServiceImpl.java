package lat.nexofood.api.modules.payment.application.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lat.nexofood.api.common.audit.AuditAction;
import lat.nexofood.api.common.audit.SecurityAuditLogger;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.PaymentGatewayException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.FrontendProperties;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoClient;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoProperties;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOAuthTokenResponse;
import lat.nexofood.api.modules.payment.web.dto.response.MercadoPagoConnectResponse;
import lat.nexofood.api.modules.payment.web.dto.response.MercadoPagoStatusResponse;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class MercadoPagoOAuthServiceImpl implements MercadoPagoOAuthService {

    private static final Duration STATE_EXPIRATION = Duration.ofMinutes(10);
    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_PURPOSE = "purpose";
    private static final String PURPOSE_MP_OAUTH = "MP_OAUTH_STATE";

    private final TenantRepository tenantRepository;
    private final MercadoPagoClient mercadoPagoClient;
    private final MercadoPagoProperties properties;
    private final FrontendProperties frontendProperties;
    private final CurrentUserProvider currentUserProvider;
    private final SecurityAuditLogger securityAuditLogger;

    @Autowired
    public MercadoPagoOAuthServiceImpl(
            TenantRepository tenantRepository,
            MercadoPagoClient mercadoPagoClient,
            MercadoPagoProperties properties,
            FrontendProperties frontendProperties,
            CurrentUserProvider currentUserProvider,
            SecurityAuditLogger securityAuditLogger
    ) {
        this.tenantRepository = tenantRepository;
        this.mercadoPagoClient = mercadoPagoClient;
        this.properties = properties;
        this.frontendProperties = frontendProperties;
        this.currentUserProvider = currentUserProvider;
        this.securityAuditLogger = securityAuditLogger != null ? securityAuditLogger : new SecurityAuditLogger();
    }

    public MercadoPagoOAuthServiceImpl(
            TenantRepository tenantRepository,
            MercadoPagoClient mercadoPagoClient,
            MercadoPagoProperties properties,
            FrontendProperties frontendProperties,
            CurrentUserProvider currentUserProvider
    ) {
        this(
                tenantRepository,
                mercadoPagoClient,
                properties,
                frontendProperties,
                currentUserProvider,
                new SecurityAuditLogger()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public MercadoPagoConnectResponse buildAuthorizationUrl(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado con ID: " + tenantId));

        if (properties.getClientId() == null || properties.getClientId().isBlank()) {
            throw new BadRequestException("El client_id de Mercado Pago no está configurado en la plataforma");
        }

        UUID actorUserId = currentUserProvider != null ? currentUserProvider.getCurrentUserId() : tenant.getOwner().getId();
        String signedState = generateStateToken(tenant.getId(), actorUserId);

        String authUrl = UriComponentsBuilder.fromUriString(properties.getAuthBaseUrl() + "/authorization")
                .queryParam("client_id", properties.getClientId())
                .queryParam("response_type", "code")
                .queryParam("platform_id", "mp")
                .queryParam("state", signedState)
                .queryParam("redirect_uri", properties.getOauth().getRedirectUri())
                .build()
                .toUriString();

        return MercadoPagoConnectResponse.builder()
                .authorizationUrl(authUrl)
                .build();
    }

    @Override
    @Transactional
    public String handleCallback(String code, String state, String error) {
        if (error != null && !error.isBlank()) {
            log.warn("Autorización OAuth de Mercado Pago rechazada o con error: {}", error);
            return frontendProperties.buildMpConnectResultUrl(false, "access_denied");
        }
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return frontendProperties.buildMpConnectResultUrl(false, "missing_params");
        }

        UUID tenantId;
        UUID actorUserId;
        try {
            Claims claims = parseAndValidateStateToken(state);
            tenantId = UUID.fromString(claims.get(CLAIM_TENANT_ID, String.class));
            actorUserId = UUID.fromString(claims.get(CLAIM_USER_ID, String.class));
        } catch (Exception ex) {
            log.warn("Token state inválido o expirado en callback OAuth de Mercado Pago: {}", ex.getMessage());
            return frontendProperties.buildMpConnectResultUrl(false, "invalid_state");
        }

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return frontendProperties.buildMpConnectResultUrl(false, "tenant_not_found");
        }

        try {
            MpOAuthTokenResponse tokenResponse = mercadoPagoClient.exchangeAuthorizationCode(code);
            if (tokenResponse == null || tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
                return frontendProperties.buildMpConnectResultUrl(false, "invalid_token_response");
            }

            OffsetDateTime now = OffsetDateTime.now();
            long expiresInSeconds = tokenResponse.expiresIn() != null && tokenResponse.expiresIn() > 0
                    ? tokenResponse.expiresIn()
                    : Duration.ofDays(180).toSeconds();

            tenant.setMpAccessToken(tokenResponse.accessToken());
            tenant.setMpRefreshToken(tokenResponse.refreshToken());
            tenant.setMpPublicKey(tokenResponse.publicKey());
            tenant.setMpUserId(tokenResponse.userId() != null ? String.valueOf(tokenResponse.userId()) : null);
            tenant.setMpConnectedAt(now);
            tenant.setMpTokenExpiresAt(now.plusSeconds(expiresInSeconds));

            tenantRepository.save(tenant);

            securityAuditLogger.logEventWithActor(
                    AuditAction.MP_CONNECTION,
                    actorUserId.toString(),
                    tenant.getId(),
                    tenant.getId().toString(),
                    "Cuenta de Mercado Pago vinculada exitosamente (mpUserId=" + tenant.getMpUserId() + ")"
            );

            return frontendProperties.buildMpConnectResultUrl(true, null);
        } catch (Exception ex) {
            log.error("Error al intercambiar código OAuth de Mercado Pago para el tenant {}", tenantId, ex);
            return frontendProperties.buildMpConnectResultUrl(false, "oauth_exchange_failed");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MercadoPagoStatusResponse getStatus(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado con ID: " + tenantId));

        return MercadoPagoStatusResponse.builder()
                .tenantId(tenant.getId())
                .connected(tenant.isMercadoPagoConnected())
                .mpUserId(tenant.getMpUserId())
                .connectedAt(tenant.getMpConnectedAt())
                .tokenExpiresAt(tenant.getMpTokenExpiresAt())
                .build();
    }

    @Override
    @Transactional
    public void disconnect(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado con ID: " + tenantId));

        String previousMpUserId = tenant.getMpUserId();
        tenant.setMpAccessToken(null);
        tenant.setMpRefreshToken(null);
        tenant.setMpPublicKey(null);
        tenant.setMpUserId(null);
        tenant.setMpConnectedAt(null);
        tenant.setMpTokenExpiresAt(null);

        tenantRepository.save(tenant);

        securityAuditLogger.logEvent(
                AuditAction.MP_CONNECTION,
                tenant.getId(),
                tenant.getId().toString(),
                "Cuenta de Mercado Pago desvinculada (mpUserId previo=" + previousMpUserId + ")"
        );
    }

    @Override
    @Transactional
    public String getValidAccessToken(Tenant tenant) {
        if (tenant == null || !tenant.isMercadoPagoConnected()) {
            throw new ResourceConflictException("Este restaurante aún no tiene habilitados los pagos en línea con Mercado Pago");
        }

        OffsetDateTime expiresAt = tenant.getMpTokenExpiresAt();
        if (expiresAt != null
                && expiresAt.isBefore(OffsetDateTime.now().plusDays(1))
                && tenant.getMpRefreshToken() != null
                && !tenant.getMpRefreshToken().isBlank()) {
            refreshTenantToken(tenant);
        }

        return tenant.getMpAccessToken();
    }

    @Override
    @Transactional
    public boolean refreshTenantToken(Tenant tenant) {
        if (tenant == null || tenant.getMpRefreshToken() == null || tenant.getMpRefreshToken().isBlank()) {
            return false;
        }
        try {
            MpOAuthTokenResponse refreshed = mercadoPagoClient.refreshToken(tenant.getMpRefreshToken());
            if (refreshed == null || refreshed.accessToken() == null || refreshed.accessToken().isBlank()) {
                return false;
            }
            OffsetDateTime now = OffsetDateTime.now();
            long expiresInSeconds = refreshed.expiresIn() != null && refreshed.expiresIn() > 0
                    ? refreshed.expiresIn()
                    : Duration.ofDays(180).toSeconds();

            tenant.setMpAccessToken(refreshed.accessToken());
            if (refreshed.refreshToken() != null && !refreshed.refreshToken().isBlank()) {
                tenant.setMpRefreshToken(refreshed.refreshToken());
            }
            if (refreshed.publicKey() != null && !refreshed.publicKey().isBlank()) {
                tenant.setMpPublicKey(refreshed.publicKey());
            }
            if (refreshed.userId() != null) {
                tenant.setMpUserId(String.valueOf(refreshed.userId()));
            }
            tenant.setMpTokenExpiresAt(now.plusSeconds(expiresInSeconds));
            tenantRepository.save(tenant);
            log.info("Token OAuth de Mercado Pago renovado exitosamente para el tenant {}", tenant.getId());
            return true;
        } catch (PaymentGatewayException ex) {
            log.warn("No se pudo renovar el token OAuth de Mercado Pago para el tenant {}: {}",
                    tenant.getId(), ex.getMessage());
            return false;
        }
    }

    public String generateStateToken(UUID tenantId, UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim(CLAIM_PURPOSE, PURPOSE_MP_OAUTH)
                .claim(CLAIM_TENANT_ID, tenantId.toString())
                .claim(CLAIM_USER_ID, userId != null ? userId.toString() : tenantId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(STATE_EXPIRATION)))
                .signWith(getSigningKey())
                .compact();
    }

    private Claims parseAndValidateStateToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String purpose = claims.get(CLAIM_PURPOSE, String.class);
        if (!PURPOSE_MP_OAUTH.equals(purpose)) {
            throw new BadRequestException("Propósito de token state inválido");
        }
        return claims;
    }

    private SecretKey getSigningKey() {
        String rawSecret = properties.getOauth() != null && properties.getOauth().getStateSecret() != null
                ? properties.getOauth().getStateSecret()
                : "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        byte[] keyBytes = rawSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
