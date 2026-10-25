package lat.nexofood.api.modules.store.web.dto.request;

import jakarta.validation.constraints.NotNull;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lombok.Builder;

@Builder
public record MemberRoleUpdateRequest(
        @NotNull(message = "El rol es obligatorio")
        TenantStaffRole role
) {}
