package lat.nexofood.api.modules.store.application.usecase.auth;

import lat.nexofood.api.modules.store.web.dto.request.StoreLoginRequest;
import lat.nexofood.api.modules.store.web.dto.response.StoreAuthResponse;

public interface StoreLoginUseCase {
    StoreAuthResponse execute(String tenantSlug, StoreLoginRequest request);
}
