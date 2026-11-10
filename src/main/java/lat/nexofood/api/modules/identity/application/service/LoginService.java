package lat.nexofood.api.modules.identity.application.service;

import lat.nexofood.api.common.audit.AuditAction;
import lat.nexofood.api.common.audit.SecurityAuditLogger;
import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.UnauthorizedException;
import lat.nexofood.api.common.security.PasswordPolicyValidator;
import lat.nexofood.api.common.security.TokenHashUtil;
import lat.nexofood.api.modules.identity.application.usecase.LoginUseCase;
import lat.nexofood.api.modules.identity.domain.RefreshToken;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.RefreshTokenRepository;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lat.nexofood.api.modules.identity.web.dto.request.LoginRequest;
import lat.nexofood.api.modules.identity.web.dto.response.AuthResponse;
import lat.nexofood.api.modules.identity.web.dto.response.TenantStaffMembershipDto;
import lat.nexofood.api.modules.identity.web.mapper.UserMapper;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lat.nexofood.api.security.jwt.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LoginService implements LoginUseCase {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final long LOCK_DURATION_MINUTES = 15L;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final TenantMemberRepository tenantMemberRepository;
    private final SecurityAuditLogger securityAuditLogger;

    public LoginService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserMapper userMapper,
            TenantMemberRepository tenantMemberRepository
    ) {
        this(userRepository, refreshTokenRepository, passwordEncoder, jwtService, userMapper, tenantMemberRepository, new SecurityAuditLogger());
    }

    @Autowired
    public LoginService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserMapper userMapper,
            TenantMemberRepository tenantMemberRepository,
            SecurityAuditLogger securityAuditLogger
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.tenantMemberRepository = tenantMemberRepository;
        this.securityAuditLogger = securityAuditLogger != null ? securityAuditLogger : new SecurityAuditLogger();
    }

    @Override
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse execute(LoginRequest request) {
        String normalizedEmail = PasswordPolicyValidator.normalizeEmail(request.email());
        log.info("Intento de inicio de sesión para el email: {}", normalizedEmail);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException(ErrorMessages.INVALID_CREDENTIALS));

        if (user.getDeletedAt() != null) {
            log.warn("Intento de login con cuenta eliminada (soft delete): {}", normalizedEmail);
            throw new UnauthorizedException(ErrorMessages.USER_INACTIVE);
        }

        if (user.isAccountLocked()) {
            log.warn("Intento de login en cuenta bloqueada temporalmente: {}", normalizedEmail);
            throw new UnauthorizedException(ErrorMessages.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean lockedNow = user.recordFailedLogin(MAX_FAILED_ATTEMPTS, LOCK_DURATION_MINUTES);
            userRepository.save(user);
            if (lockedNow) {
                securityAuditLogger.logEventWithActor(
                        AuditAction.ACCOUNT_LOCKOUT,
                        normalizedEmail,
                        null,
                        user.getId() != null ? user.getId().toString() : normalizedEmail,
                        "Cuenta bloqueada temporalmente tras " + user.getFailedLoginAttempts() + " intentos fallidos"
                );
            }
            log.warn("Contraseña incorrecta para el usuario: {}", normalizedEmail);
            throw new UnauthorizedException(ErrorMessages.INVALID_CREDENTIALS);
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            log.warn("Intento de login con cuenta inactiva: {}", normalizedEmail);
            throw new UnauthorizedException(ErrorMessages.USER_INACTIVE);
        }

        if ((user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0)
                || user.getLockedUntil() != null) {
            user.resetFailedLoginAttempts();
            userRepository.save(user);
        }

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

        List<TenantStaffMembershipDto> staffMemberships = tenantMemberRepository
                .findAllByUserId(user.getId())
                .stream()
                .map(member -> TenantStaffMembershipDto.builder()
                        .tenantId(member.getTenant().getId())
                        .tenantName(member.getTenant().getName())
                        .tenantSlug(member.getTenant().getSlug())
                        .staffRole(member.getRole())
                        .build())
                .collect(Collectors.toList());

        log.info("Inicio de sesión exitoso para el usuario con ID: {}", user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime() / 1000)
                .user(userMapper.toResponse(user))
                .staffMemberships(staffMemberships)
                .build();
    }
}
