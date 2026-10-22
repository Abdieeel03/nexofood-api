package lat.nexofood.api.modules.identity.application.usecase;

import lat.nexofood.api.modules.identity.web.dto.request.RefreshTokenRequest;

public interface LogoutUseCase {
    void execute(RefreshTokenRequest request);
}
