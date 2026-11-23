package lat.nexofood.api.modules.store.application.usecase.tenant;

import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import org.springframework.data.domain.Pageable;

public interface GetAllTenantsUseCase {

    PageResponse<TenantResponse> execute(Boolean isActive, Boolean isOpen, String search, Pageable pageable);
}
