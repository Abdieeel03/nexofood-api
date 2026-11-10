package lat.nexofood.api.modules.store.application.usecase.auth;

import lat.nexofood.api.modules.store.web.dto.request.StoreRegisterRequest;
import lat.nexofood.api.modules.store.web.dto.response.StoreAuthResponse;

public interface StoreRegisterUseCase {
    StoreAuthResponse execute(String tenantSlug, StoreRegisterRequest request);
}
