package lat.nexofood.api.modules.store.web.dto.response;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record TenantCustomerResponse(
        UUID id,
        UUID tenantId,
        String tenantName,
        String tenantSlug,
        UUID userId,
        String userFullName,
        String userEmail,
        Integer loyaltyPoints,
        Boolean isBlocked,
        String notes,
        Integer totalOrders,
        OffsetDateTime firstOrderAt,
        OffsetDateTime lastOrderAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
