package lat.nexofood.api.modules.store.web.dto.request;

import lombok.Builder;

@Builder
public record TenantCustomerUpdateRequest(
        String notes,
        Integer loyaltyPointsAdjustment,
        Boolean isBlocked
) {}
