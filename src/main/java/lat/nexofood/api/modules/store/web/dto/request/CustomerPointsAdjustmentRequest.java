package lat.nexofood.api.modules.store.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record CustomerPointsAdjustmentRequest(
        @NotNull(message = "El campo 'adjustment' es obligatorio")
        @Min(value = -100000, message = "El ajuste no puede ser menor a -100,000")
        @Max(value = 100000, message = "El ajuste no puede ser mayor a 100,000")
        Integer adjustment
) {}
