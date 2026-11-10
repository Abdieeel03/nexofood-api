package lat.nexofood.api.modules.store.application.service.customer;

import lat.nexofood.api.modules.store.application.usecase.tenant.*;
import lat.nexofood.api.modules.store.application.usecase.member.*;
import lat.nexofood.api.modules.store.application.usecase.customer.*;
import lat.nexofood.api.modules.store.application.usecase.auth.*;
import lat.nexofood.api.modules.store.web.dto.response.TenantCustomerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantCustomerServiceImpl implements TenantCustomerService {

    private final GetTenantCustomersUseCase getTenantCustomersUseCase;
    private final GetTenantCustomerByIdUseCase getTenantCustomerByIdUseCase;
    private final BlockTenantCustomerUseCase blockTenantCustomerUseCase;
    private final UpdateTenantCustomerNotesUseCase updateTenantCustomerNotesUseCase;
    private final AdjustLoyaltyPointsUseCase adjustLoyaltyPointsUseCase;

    @Override
    public Page<TenantCustomerResponse> getCustomers(UUID tenantId, Pageable pageable) {
        return getTenantCustomersUseCase.execute(tenantId, pageable);
    }

    @Override
    public TenantCustomerResponse getCustomerById(UUID tenantId, UUID customerId) {
        return getTenantCustomerByIdUseCase.execute(tenantId, customerId);
    }

    @Override
    public TenantCustomerResponse blockCustomer(UUID tenantId, UUID customerId, boolean isBlocked) {
        return blockTenantCustomerUseCase.execute(tenantId, customerId, isBlocked);
    }

    @Override
    public TenantCustomerResponse updateNotes(UUID tenantId, UUID customerId, String notes) {
        return updateTenantCustomerNotesUseCase.execute(tenantId, customerId, notes);
    }

    @Override
    public TenantCustomerResponse adjustPoints(UUID tenantId, UUID customerId, int adjustment) {
        return adjustLoyaltyPointsUseCase.execute(tenantId, customerId, adjustment);
    }
}
