package lat.nexofood.api.modules.store.application.service.customer;

import lat.nexofood.api.modules.store.web.dto.response.TenantCustomerResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TenantCustomerService {

    Page<TenantCustomerResponse> getCustomers(UUID tenantId, Pageable pageable);

    TenantCustomerResponse getCustomerById(UUID tenantId, UUID customerId);

    TenantCustomerResponse blockCustomer(UUID tenantId, UUID customerId, boolean isBlocked);

    TenantCustomerResponse updateNotes(UUID tenantId, UUID customerId, String notes);

    TenantCustomerResponse adjustPoints(UUID tenantId, UUID customerId, int adjustment);
}
