package lat.nexofood.api.modules.store.web.mapper;

import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.web.dto.response.TenantCustomerResponse;
import org.springframework.stereotype.Component;

@Component
public class TenantCustomerMapper {

    public TenantCustomerResponse toResponse(TenantCustomer tc) {
        if (tc == null) return null;
        return TenantCustomerResponse.builder()
                .id(tc.getId())
                .tenantId(tc.getTenant() != null ? tc.getTenant().getId() : null)
                .tenantName(tc.getTenant() != null ? tc.getTenant().getName() : null)
                .tenantSlug(tc.getTenant() != null ? tc.getTenant().getSlug() : null)
                .userId(tc.getUser() != null ? tc.getUser().getId() : null)
                .userFullName(tc.getUser() != null ? tc.getUser().getFullName() : null)
                .userEmail(tc.getUser() != null ? tc.getUser().getEmail() : null)
                .loyaltyPoints(tc.getLoyaltyPoints())
                .isBlocked(tc.getIsBlocked())
                .notes(tc.getNotes())
                .totalOrders(tc.getTotalOrders())
                .firstOrderAt(tc.getFirstOrderAt())
                .lastOrderAt(tc.getLastOrderAt())
                .createdAt(tc.getCreatedAt())
                .updatedAt(tc.getUpdatedAt())
                .build();
    }
}
