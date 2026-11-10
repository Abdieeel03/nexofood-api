package lat.nexofood.api.modules.store.application.usecase.customer;

import lat.nexofood.api.modules.store.web.dto.response.TenantCustomerResponse;

import java.util.UUID;

public interface AdjustLoyaltyPointsUseCase {
    TenantCustomerResponse execute(UUID tenantId, UUID customerId, int pointsAdjustment);
}
