package lat.nexofood.api.modules.store.application.service.auth;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.exception.UnauthorizedException;
import lat.nexofood.api.modules.identity.domain.RefreshToken;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.RefreshTokenRepository;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lat.nexofood.api.modules.identity.web.mapper.UserMapper;
import lat.nexofood.api.modules.store.application.usecase.auth.StoreLoginUseCase;
import lat.nexofood.api.modules.store.web.dto.request.StoreLoginRequest;
import lat.nexofood.api.modules.store.web.dto.response.StoreAuthResponse;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantCustomerRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.common.security.TokenHashUtil;
import lat.nexofood.api.modules.store.web.mapper.TenantCustomerMapper;
import lat.nexofood.api.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreLoginService implements StoreLoginUseCase {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantCustomerRepository tenantCustomerRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final TenantCustomerMapper tenantCustomerMapper;

    @Override
    @Transactional
    public StoreAuthResponse execute(String tenantSlug, StoreLoginRequest request) {
        // 1. Validar credenciales contra User global
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException(ErrorMessages.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(ErrorMessages.INVALID_CREDENTIALS);
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException(ErrorMessages.USER_INACTIVE);
        }

        // 2. Validar que el tenant exista
        Tenant tenant = tenantRepository.findBySlug(tenantSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado: " + tenantSlug));

        // 3. Autoasociación: si no existe TenantCustomer para este tenant, crearlo
        TenantCustomer tenantCustomer = tenantCustomerRepository
                .findByTenantIdAndUserId(tenant.getId(), user.getId())
                .orElseGet(() -> {
                    log.info("Autoasociando usuario {} al tenant '{}'", user.getId(), tenantSlug);
                    TenantCustomer newCustomer = TenantCustomer.builder()
                            .tenant(tenant)
                            .user(user)
                            .build();
                    return tenantCustomerRepository.save(newCustomer);
                });

        // 4. Verificación de bloqueo
        if (Boolean.TRUE.equals(tenantCustomer.getIsBlocked())) {
            throw new UnauthorizedException("Tu cuenta ha sido bloqueada por este comercio. " +
                    "Por favor contacta al restaurante para más información.");
        }

        // 5. Rotar refresh token y emitir tokens JWT
        refreshTokenRepository.revokeAllUserTokens(user.getEmail());

        String accessToken = jwtService.generateToken(user);
        String refreshTokenStr = jwtService.generateRefreshToken(user);
        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(TokenHashUtil.hash(refreshTokenStr))
                .user(user)
                .expiryDate(LocalDateTime.now().plus(jwtService.getRefreshExpirationTime(), ChronoUnit.MILLIS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        log.info("Login exitoso en tienda '{}' para userId: {}", tenantSlug, user.getId());

        return StoreAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime() / 1000)
                .user(userMapper.toResponse(user))
                .customerProfile(tenantCustomerMapper.toResponse(tenantCustomer))
                .build();
    }
}
