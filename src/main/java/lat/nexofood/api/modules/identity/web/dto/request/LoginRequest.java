package lat.nexofood.api.modules.identity.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record LoginRequest(
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "Formato de email inválido")
        @Size(max = 255)
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        String password
) {}
