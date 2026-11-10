package lat.nexofood.api.modules.identity.application.usecase.profile;

import lat.nexofood.api.modules.identity.web.dto.request.UserUpdateRequest;
import lat.nexofood.api.modules.identity.web.dto.response.UserResponse;

public interface UpdateProfileUseCase {
    UserResponse execute(UserUpdateRequest request);
}
