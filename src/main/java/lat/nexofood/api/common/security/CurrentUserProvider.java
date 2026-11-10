package lat.nexofood.api.common.security;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.UnauthorizedException;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.domain.UserSystemRole;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public Optional<String> getCurrentEmailOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.ofNullable(auth.getName());
    }

    public String getCurrentEmail() {
        return getCurrentEmailOptional()
                .orElseThrow(() -> new UnauthorizedException("Usuario no autenticado"));
    }

    public User getCurrentUser() {
        String email = getCurrentEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException(ErrorMessages.USER_NOT_FOUND));
        if (user.getDeletedAt() != null || !Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException(ErrorMessages.USER_INACTIVE);
        }
        return user;
    }

    public UUID getCurrentUserId() {
        return getCurrentUser().getId();
    }

    public boolean isSuperAdmin() {
        return getCurrentEmailOptional()
                .flatMap(userRepository::findByEmail)
                .map(user -> user.getSystemRole() == UserSystemRole.SUPERADMIN)
                .orElse(false);
    }
}
