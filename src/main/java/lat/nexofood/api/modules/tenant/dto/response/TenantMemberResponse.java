package lat.nexofood.api.modules.tenant.dto.response;

import lat.nexofood.api.modules.tenant.domain.TenantStaffRole;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record TenantMemberResponse(
        UUID id,
        UUID tenantId,
        UUID userId,
        String userFullName,
        String userEmail,
        TenantStaffRole role,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
