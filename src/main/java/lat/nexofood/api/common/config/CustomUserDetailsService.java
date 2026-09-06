package lat.nexofood.api.common.config;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.modules.auth.domain.User;
import lat.nexofood.api.modules.auth.repository.UserRepository;
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

        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        ErrorMessages.USER_NOT_FOUND));

        return org.springframework.security.core.userdetails.User
                .builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .roles(user.getSystemRole().name())
                .disabled(!Boolean.TRUE.equals(user.getIsActive()))
                .build();
    }

    public UserDetails findByEmail(String email) throws UsernameNotFoundException {
        return loadUserByUsername(email);
    }
}