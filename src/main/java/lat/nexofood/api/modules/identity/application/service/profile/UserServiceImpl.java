package lat.nexofood.api.modules.identity.application.service.profile;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.identity.application.usecase.profile.ChangePasswordUseCase;
import lat.nexofood.api.modules.identity.application.usecase.profile.DeleteMyAccountUseCase;
import lat.nexofood.api.modules.identity.application.usecase.profile.UpdateProfileUseCase;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.RefreshTokenRepository;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lat.nexofood.api.modules.identity.web.dto.request.ChangePasswordRequest;
import lat.nexofood.api.modules.identity.web.dto.request.UserUpdateRequest;
import lat.nexofood.api.modules.identity.web.dto.response.UserResponse;
import lat.nexofood.api.modules.identity.web.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UpdateProfileUseCase updateProfileUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final DeleteMyAccountUseCase deleteMyAccountUseCase;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;

    @Override
    public UserResponse updateMyProfile(UserUpdateRequest request) {
        return updateProfileUseCase.execute(request);
    }

    @Override
    public void changeMyPassword(ChangePasswordRequest request) {
        changePasswordUseCase.execute(request);
    }

    @Override
    public void deleteMyAccount() {
        deleteMyAccountUseCase.execute();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toResponse);
    }

    @Override
    @Transactional
    public UserResponse changeUserStatus(UUID userId, boolean isActive) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.USER_NOT_FOUND));

        user.setIsActive(isActive);
        User updated = userRepository.save(user);

        if (!isActive) {
            refreshTokenRepository.revokeAllUserTokens(user.getEmail());
        }

        log.info("Estado del usuario {} cambiado a isActive={}", userId, isActive);
        return userMapper.toResponse(updated);
    }
}
