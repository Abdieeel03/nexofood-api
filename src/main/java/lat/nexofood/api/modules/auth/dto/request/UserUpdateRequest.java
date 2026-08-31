package lat.nexofood.api.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UserUpdateRequest(
        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 150)
        String fullName,

        @Size(max = 20)
        String phone
) {}
