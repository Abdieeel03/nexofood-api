package lat.nexofood.api.modules.identity.application.service.profile;

import lat.nexofood.api.modules.identity.web.dto.request.ChangePasswordRequest;
import lat.nexofood.api.modules.identity.web.dto.request.UserUpdateRequest;
import lat.nexofood.api.modules.identity.web.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {

    UserResponse updateMyProfile(UserUpdateRequest request);

    void changeMyPassword(ChangePasswordRequest request);

    void deleteMyAccount();

    Page<UserResponse> getAllUsers(Pageable pageable);

    UserResponse changeUserStatus(UUID userId, boolean isActive);
}
