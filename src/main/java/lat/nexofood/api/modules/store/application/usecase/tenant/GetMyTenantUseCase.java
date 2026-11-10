package lat.nexofood.api.modules.store.application.usecase.tenant;

import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;

public interface GetMyTenantUseCase {
    TenantResponse execute(String email);
}
