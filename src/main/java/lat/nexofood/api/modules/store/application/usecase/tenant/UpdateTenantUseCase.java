package lat.nexofood.api.modules.store.application.usecase.tenant;

import lat.nexofood.api.modules.store.web.dto.request.TenantUpdateRequest;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;

import java.util.UUID;

public interface UpdateTenantUseCase {
    TenantResponse execute(UUID id, TenantUpdateRequest request);
}
