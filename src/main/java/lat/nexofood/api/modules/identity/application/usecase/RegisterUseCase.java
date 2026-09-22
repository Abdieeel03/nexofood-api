package lat.nexofood.api.modules.identity.application.usecase;

import lat.nexofood.api.modules.identity.web.dto.request.UserRegisterRequest;
import lat.nexofood.api.modules.identity.web.dto.response.AuthResponse;

public interface RegisterUseCase {
    AuthResponse execute(UserRegisterRequest request);
}
