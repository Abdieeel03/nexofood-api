package lat.nexofood.api.modules.identity.application.service.profile;

import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.application.usecase.profile.DeleteMyAccountUseCase;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.RefreshTokenRepository;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteMyAccountService implements DeleteMyAccountUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public void execute() {
        User user = currentUserProvider.getCurrentUser();
        user.setIsActive(false);
        user.setDeletedAt(OffsetDateTime.now());
        userRepository.save(user);

        refreshTokenRepository.revokeAllUserTokens(user.getEmail());
        log.info("Cuenta eliminada lógicamente (soft delete) para el usuario {}", user.getId());
    }
}
