package lat.nexofood.api.modules.identity.application.usecase;

import lat.nexofood.api.modules.identity.web.dto.request.LoginRequest;
import lat.nexofood.api.modules.identity.web.dto.response.AuthResponse;

public interface LoginUseCase {
    AuthResponse execute(LoginRequest request);
}
