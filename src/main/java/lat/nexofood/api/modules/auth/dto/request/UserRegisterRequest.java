package lat.nexofood.api.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UserRegisterRequest(
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "Formato de email inválido")
        @Size(max = 255)
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
        String password,

        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 150)
        String fullName,

        @Size(max = 20)
        String phone
) {}
