package lat.nexofood.api.modules.identity.web.dto.response;

import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lombok.Builder;

import java.util.UUID;

@Builder
public record TenantStaffMembershipDto(
        UUID tenantId,
        String tenantName,
        String tenantSlug,
        TenantStaffRole staffRole
) {}
