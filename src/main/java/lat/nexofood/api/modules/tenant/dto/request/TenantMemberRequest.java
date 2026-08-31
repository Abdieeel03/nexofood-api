package lat.nexofood.api.modules.tenant.dto.request;

import jakarta.validation.constraints.NotNull;
import lat.nexofood.api.modules.tenant.domain.TenantStaffRole;
import lombok.Builder;

import java.util.UUID;

@Builder
public record TenantMemberRequest(
        @NotNull(message = "El ID del usuario es obligatorio")
        UUID userId,

        @NotNull(message = "El rol del miembro es obligatorio")
        TenantStaffRole role
) {}
