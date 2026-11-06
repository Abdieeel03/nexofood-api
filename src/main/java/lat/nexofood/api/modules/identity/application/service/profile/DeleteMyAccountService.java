package lat.nexofood.api.modules.identity.application.service.profile;

import lat.nexofood.api.common.audit.AuditAction;
import lat.nexofood.api.common.audit.SecurityAuditLogger;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.application.usecase.profile.DeleteMyAccountUseCase;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.RefreshTokenRepository;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
public class DeleteMyAccountService implements DeleteMyAccountUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SecurityAuditLogger securityAuditLogger;

    public DeleteMyAccountService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this(userRepository, refreshTokenRepository, currentUserProvider, new SecurityAuditLogger());
    }

    @Autowired
    public DeleteMyAccountService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            CurrentUserProvider currentUserProvider,
            SecurityAuditLogger securityAuditLogger
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.currentUserProvider = currentUserProvider;
        this.securityAuditLogger = securityAuditLogger != null ? securityAuditLogger : new SecurityAuditLogger();
    }

    @Override
    @Transactional
    public void execute() {
        User user = currentUserProvider.getCurrentUser();
        user.setIsActive(false);
        user.setDeletedAt(OffsetDateTime.now());
        user.incrementTokenVersion();
        userRepository.save(user);

        refreshTokenRepository.revokeAllUserTokens(user.getEmail());
        securityAuditLogger.logEventWithActor(
                AuditAction.ACCOUNT_DELETED,
                user.getEmail(),
                null,
                user.getId() != null ? user.getId().toString() : user.getEmail(),
                "Baja lógica de cuenta de usuario"
        );
        log.info("Cuenta eliminada lógicamente (soft delete) para el usuario {}", user.getId());
    }
}
