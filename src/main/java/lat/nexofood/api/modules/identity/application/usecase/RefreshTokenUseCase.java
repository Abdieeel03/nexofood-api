package lat.nexofood.api.modules.identity.application.usecase;

import lat.nexofood.api.modules.identity.web.dto.request.RefreshTokenRequest;
import lat.nexofood.api.modules.identity.web.dto.response.AuthResponse;

public interface RefreshTokenUseCase {
    AuthResponse execute(RefreshTokenRequest request);
}
