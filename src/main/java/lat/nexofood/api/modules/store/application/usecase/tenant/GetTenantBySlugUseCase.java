package lat.nexofood.api.modules.store.application.usecase.tenant;

import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;

public interface GetTenantBySlugUseCase {
    TenantResponse execute(String slug);
}
