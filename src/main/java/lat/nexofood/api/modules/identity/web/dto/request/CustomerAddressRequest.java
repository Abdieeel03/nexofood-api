package lat.nexofood.api.modules.identity.web.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CustomerAddressRequest(
        @Size(max = 50)
        String title,

        @NotBlank(message = "La dirección es obligatoria")
        @Size(max = 255, message = "La dirección no puede exceder 255 caracteres")
        String addressLine,

        @Size(max = 500, message = "La referencia no puede exceder 500 caracteres")
        String reference,

        @NotNull(message = "La latitud es obligatoria")
        @DecimalMin(value = "-90.0", message = "La latitud debe estar entre -90.0 y 90.0 grados")
        @DecimalMax(value = "90.0", message = "La latitud debe estar entre -90.0 y 90.0 grados")
        Double latitude,

        @NotNull(message = "La longitud es obligatoria")
        @DecimalMin(value = "-180.0", message = "La longitud debe estar entre -180.0 y 180.0 grados")
        @DecimalMax(value = "180.0", message = "La longitud debe estar entre -180.0 y 180.0 grados")
        Double longitude
) {}
