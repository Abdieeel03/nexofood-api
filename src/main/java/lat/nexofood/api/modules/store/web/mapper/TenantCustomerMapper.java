package lat.nexofood.api.modules.store.web.mapper;

import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.web.dto.response.TenantCustomerResponse;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TenantCustomerMapper {

    public TenantCustomerResponse toResponse(TenantCustomer tc) {
        if (tc == null) return null;

        UUID tenantId = tc.getTenant() != null && tc.getTenant().getId() != null
                ? tc.getTenant().getId()
                : (tc.getId() != null ? tc.getId().getTenantId() : null);

        UUID userId = tc.getUser() != null && tc.getUser().getId() != null
                ? tc.getUser().getId()
                : (tc.getId() != null ? tc.getId().getUserId() : null);

        return TenantCustomerResponse.builder()
                .id(userId)
                .tenantId(tenantId)
                .tenantName(tc.getTenant() != null ? tc.getTenant().getName() : null)
                .tenantSlug(tc.getTenant() != null ? tc.getTenant().getSlug() : null)
                .userId(userId)
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
