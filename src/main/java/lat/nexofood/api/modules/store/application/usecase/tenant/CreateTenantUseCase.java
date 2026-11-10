package lat.nexofood.api.modules.store.application.usecase.tenant;

import lat.nexofood.api.modules.store.web.dto.request.TenantCreateRequest;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;

public interface CreateTenantUseCase {
    TenantResponse execute(TenantCreateRequest request);
}
