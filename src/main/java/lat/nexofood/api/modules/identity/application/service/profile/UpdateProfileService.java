package lat.nexofood.api.modules.identity.application.service.profile;

import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.application.usecase.profile.UpdateProfileUseCase;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lat.nexofood.api.modules.identity.web.dto.request.UserUpdateRequest;
import lat.nexofood.api.modules.identity.web.dto.response.UserResponse;
import lat.nexofood.api.modules.identity.web.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateProfileService implements UpdateProfileUseCase {

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserResponse execute(UserUpdateRequest request) {
        User user = currentUserProvider.getCurrentUser();
        user.setFullName(request.fullName());
        user.setPhone(request.phone());

        User updated = userRepository.save(user);
        log.info("Perfil actualizado para el usuario {}", user.getId());
        return userMapper.toResponse(updated);
    }
}
