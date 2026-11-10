package lat.nexofood.api.modules.store.application.service.auth;

import lat.nexofood.api.modules.store.application.usecase.auth.StoreLoginUseCase;
import lat.nexofood.api.modules.store.application.usecase.auth.StoreRegisterUseCase;
import lat.nexofood.api.modules.store.web.dto.request.StoreLoginRequest;
import lat.nexofood.api.modules.store.web.dto.request.StoreRegisterRequest;
import lat.nexofood.api.modules.store.web.dto.response.StoreAuthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StoreAuthServiceImpl implements StoreAuthService {

    private final StoreRegisterUseCase storeRegisterUseCase;
    private final StoreLoginUseCase storeLoginUseCase;

    @Override
    public StoreAuthResponse register(String tenantSlug, StoreRegisterRequest request) {
        return storeRegisterUseCase.execute(tenantSlug, request);
    }

    @Override
    public StoreAuthResponse login(String tenantSlug, StoreLoginRequest request) {
        return storeLoginUseCase.execute(tenantSlug, request);
    }
}
