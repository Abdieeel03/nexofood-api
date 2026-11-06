package lat.nexofood.api.security.custom;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.security.PasswordPolicyValidator;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = PasswordPolicyValidator.normalizeEmail(email);

        User user = userRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException(
                        ErrorMessages.USER_NOT_FOUND));

        if (user.getDeletedAt() != null) {
            throw new UsernameNotFoundException(ErrorMessages.USER_NOT_FOUND);
        }

        return new CustomUserDetails(user);
    }

    public UserDetails findByEmail(String email) throws UsernameNotFoundException {
        return loadUserByUsername(email);
    }
}
