package lat.nexofood.api.modules.store.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lombok.Builder;

@Builder
public record TenantMemberRequest(
        @NotBlank(message = "El correo electrónico del usuario es obligatorio")
        @Email(message = "Debe proporcionar un correo electrónico válido")
        String email,

        @NotNull(message = "El rol del miembro es obligatorio")
        TenantStaffRole role
) {}
