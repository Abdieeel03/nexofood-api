package lat.nexofood.api.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CustomerAddressRequest(
        @Size(max = 50)
        String title,

        @NotBlank(message = "La dirección es obligatoria")
        String addressLine,

        String reference,

        @NotNull(message = "La latitud es obligatoria")
        Double latitude,

        @NotNull(message = "La longitud es obligatoria")
        Double longitude
) {}
