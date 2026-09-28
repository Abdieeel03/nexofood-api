package lat.nexofood.api.modules.store.application.usecase.customer;

import lat.nexofood.api.modules.store.web.dto.response.TenantCustomerResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface GetTenantCustomersUseCase {
    Page<TenantCustomerResponse> execute(UUID tenantId, Pageable pageable);
}
