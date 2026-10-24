package lat.nexofood.api.modules.identity.application.usecase.profile;

import lat.nexofood.api.modules.identity.web.dto.request.ChangePasswordRequest;

public interface ChangePasswordUseCase {
    void execute(ChangePasswordRequest request);
}
