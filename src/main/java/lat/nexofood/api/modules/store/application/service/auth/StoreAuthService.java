package lat.nexofood.api.modules.store.application.service.auth;

import lat.nexofood.api.modules.store.web.dto.request.StoreLoginRequest;
import lat.nexofood.api.modules.store.web.dto.request.StoreRegisterRequest;
import lat.nexofood.api.modules.store.web.dto.response.StoreAuthResponse;

public interface StoreAuthService {

    StoreAuthResponse register(String tenantSlug, StoreRegisterRequest request);

    StoreAuthResponse login(String tenantSlug, StoreLoginRequest request);
}
