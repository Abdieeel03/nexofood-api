package lat.nexofood.api.modules.identity.application.usecase;

import lat.nexofood.api.modules.identity.web.dto.response.UserProfileResponse;

public interface GetUserProfileUseCase {
    UserProfileResponse execute(String email);
}
