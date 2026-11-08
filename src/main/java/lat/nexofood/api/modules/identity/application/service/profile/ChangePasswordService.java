package lat.nexofood.api.modules.identity.application.service.profile;

import lat.nexofood.api.common.audit.AuditAction;
import lat.nexofood.api.common.audit.SecurityAuditLogger;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.common.security.PasswordPolicyValidator;
import lat.nexofood.api.modules.identity.application.usecase.profile.ChangePasswordUseCase;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.RefreshTokenRepository;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lat.nexofood.api.modules.identity.web.dto.request.ChangePasswordRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ChangePasswordService implements ChangePasswordUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditLogger securityAuditLogger;

    public ChangePasswordService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            CurrentUserProvider currentUserProvider,
            PasswordEncoder passwordEncoder
    ) {
        this(userRepository, refreshTokenRepository, currentUserProvider, passwordEncoder, new SecurityAuditLogger());
    }

    @Autowired
    public ChangePasswordService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            CurrentUserProvider currentUserProvider,
            PasswordEncoder passwordEncoder,
            SecurityAuditLogger securityAuditLogger
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.currentUserProvider = currentUserProvider;
        this.passwordEncoder = passwordEncoder;
        this.securityAuditLogger = securityAuditLogger != null ? securityAuditLogger : new SecurityAuditLogger();
    }

    @Override
    @Transactional
    public void execute(ChangePasswordRequest request) {
        User user = currentUserProvider.getCurrentUser();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("La contraseña actual es incorrecta");
        }

        PasswordPolicyValidator.validatePassword(request.newPassword());

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("La nueva contraseña debe ser diferente a la actual");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.incrementTokenVersion();
        userRepository.save(user);

        refreshTokenRepository.revokeAllUserTokens(user.getEmail());
        securityAuditLogger.logEventWithActor(
                AuditAction.PASSWORD_CHANGE,
                user.getEmail(),
                null,
                user.getId() != null ? user.getId().toString() : user.getEmail(),
                "Contraseña cambiada e invalidación de tokenVersion=" + user.getTokenVersion()
        );
        log.info("Contraseña actualizada, tokenVersion incrementado y refresh tokens revocados para el usuario {}", user.getId());
    }
}
