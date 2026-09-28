package lat.nexofood.api.modules.store.application.usecase.tenant;

import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;

import java.util.UUID;

public interface GetTenantByIdUseCase {
    TenantResponse execute(UUID id);
}
