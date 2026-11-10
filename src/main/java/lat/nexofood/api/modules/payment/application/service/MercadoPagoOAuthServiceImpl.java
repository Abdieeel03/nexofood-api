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

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
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

        String resolvedClientId = resolveClientId();
        if (resolvedClientId == null || resolvedClientId.isBlank()) {
            throw new BadRequestException("El client_id de Mercado Pago no está configurado en la plataforma");
        }

        UUID actorUserId = currentUserProvider != null ? currentUserProvider.getCurrentUserId() : tenant.getOwner().getId();
        String signedState = generateStateToken(tenant.getId(), actorUserId);

        if (isTestCredentialsMode()) {
            String testCallbackUrl = UriComponentsBuilder.fromUriString(properties.getOauth().getRedirectUri())
                    .queryParam("code", "TG-TEST-" + tenant.getId())
                    .queryParam("state", signedState)
                    .build()
                    .toUriString();
            return MercadoPagoConnectResponse.builder()
                    .authorizationUrl(testCallbackUrl)
                    .expiresInSeconds(STATE_EXPIRATION.toSeconds())
                    .build();
        }

        String authUrl = UriComponentsBuilder.fromUriString(properties.getAuthBaseUrl() + "/authorization")
                .queryParam("client_id", resolvedClientId)
                .queryParam("response_type", "code")
                .queryParam("platform_id", "mp")
                .queryParam("state", signedState)
                .queryParam("redirect_uri", properties.getOauth().getRedirectUri())
                .build()
                .toUriString();

        return MercadoPagoConnectResponse.builder()
                .authorizationUrl(authUrl)
                .expiresInSeconds(STATE_EXPIRATION.toSeconds())
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
            MpOAuthTokenResponse tokenResponse;
            if (isTestCredentialsMode()) {
                log.info("Modo Credenciales de Prueba detectado en callback OAuth para tenant {}. Vinculando Seller Test User de prueba.", tenantId);
                String testToken = properties.getPlatformAccessToken().trim();
                Long parsedUserId = extractUserIdFromToken(testToken);
                tokenResponse = MpOAuthTokenResponse.builder()
                        .accessToken(testToken)
                        .refreshToken("TEST-REFRESH-" + tenantId)
                        .userId(parsedUserId)
                        .expiresIn(Duration.ofDays(180).toSeconds())
                        .build();
            } else {
                tokenResponse = mercadoPagoClient.exchangeAuthorizationCode(code);
            }
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

    private boolean isTestCredentialsMode() {
        boolean missingSecret = properties.getClientSecret() == null || properties.getClientSecret().isBlank();
        boolean hasPlatformToken = properties.getPlatformAccessToken() != null && !properties.getPlatformAccessToken().isBlank();
        return missingSecret && hasPlatformToken;
    }

    private Long extractUserIdFromToken(String accessToken) {
        if (accessToken == null || !accessToken.contains("-")) {
            return null;
        }
        String lastPart = accessToken.substring(accessToken.lastIndexOf('-') + 1).trim();
        try {
            return Long.parseLong(lastPart);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveClientId() {
        if (properties.getClientId() != null && !properties.getClientId().isBlank()) {
            return properties.getClientId().trim();
        }
        return null;
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
        UUID effectiveUserId = userId != null ? userId : tenantId;
        long expEpochSeconds = Instant.now().plus(STATE_EXPIRATION).getEpochSecond();

        ByteBuffer payloadBuffer = ByteBuffer.allocate(40);
        payloadBuffer.putLong(tenantId.getMostSignificantBits());
        payloadBuffer.putLong(tenantId.getLeastSignificantBits());
        payloadBuffer.putLong(effectiveUserId.getMostSignificantBits());
        payloadBuffer.putLong(effectiveUserId.getLeastSignificantBits());
        payloadBuffer.putLong(expEpochSeconds);
        byte[] payloadBytes = payloadBuffer.array();

        byte[] signature = computeHmacSha256Truncated(payloadBytes);

        ByteBuffer tokenBuffer = ByteBuffer.allocate(56);
        tokenBuffer.put(payloadBytes);
        tokenBuffer.put(signature);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBuffer.array());
    }

    private Claims parseAndValidateStateToken(String token) {
        if (token != null && !token.contains(".")) {
            byte[] raw = Base64.getUrlDecoder().decode(token);
            if (raw.length != 56) {
                throw new BadRequestException("Formato de token state inválido");
            }
            byte[] payloadBytes = Arrays.copyOfRange(raw, 0, 40);
            byte[] providedSig = Arrays.copyOfRange(raw, 40, 56);
            byte[] expectedSig = computeHmacSha256Truncated(payloadBytes);
            if (!MessageDigest.isEqual(providedSig, expectedSig)) {
                throw new BadRequestException("Firma de token state inválida");
            }

            ByteBuffer buf = ByteBuffer.wrap(payloadBytes);
            UUID tenantId = new UUID(buf.getLong(), buf.getLong());
            UUID userId = new UUID(buf.getLong(), buf.getLong());
            long expEpochSeconds = buf.getLong();
            if (Instant.now().getEpochSecond() > expEpochSeconds) {
                throw new BadRequestException("Token state expirado");
            }

            return Jwts.claims()
                    .add(CLAIM_PURPOSE, PURPOSE_MP_OAUTH)
                    .add(CLAIM_TENANT_ID, tenantId.toString())
                    .add(CLAIM_USER_ID, userId.toString())
                    .build();
        }

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

    private byte[] computeHmacSha256Truncated(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(getSigningKey());
            byte[] fullHmac = mac.doFinal(data);
            return Arrays.copyOf(fullHmac, 16);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo firmar el token state OAuth", ex);
        }
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
