package lat.nexofood.api.modules.tracking.web.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record DriverLocationUpdateRequest(
        @NotNull(message = "La latitud es obligatoria")
        @DecimalMin(value = "-90.0", message = "La latitud debe estar entre -90.0 y 90.0 grados")
        @DecimalMax(value = "90.0", message = "La latitud debe estar entre -90.0 y 90.0 grados")
        Double latitude,

        @NotNull(message = "La longitud es obligatoria")
        @DecimalMin(value = "-180.0", message = "La longitud debe estar entre -180.0 y 180.0 grados")
        @DecimalMax(value = "180.0", message = "La longitud debe estar entre -180.0 y 180.0 grados")
        Double longitude,

        @DecimalMin(value = "0.0", message = "El rumbo (heading) debe estar entre 0 y 360 grados")
        @DecimalMax(value = "360.0", message = "El rumbo (heading) debe estar entre 0 y 360 grados")
        Double heading,

        @DecimalMin(value = "0.0", message = "La velocidad no puede ser negativa")
        @DecimalMax(value = "200.0", message = "La velocidad reportada excede el máximo permitido")
        Double speedKmh,

        @DecimalMin(value = "0.0", message = "La precisión GPS no puede ser negativa")
        Double accuracyM
) {}

