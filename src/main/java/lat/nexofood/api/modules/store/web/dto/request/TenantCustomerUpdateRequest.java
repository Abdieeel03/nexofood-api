package lat.nexofood.api.modules.store.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record TenantCustomerUpdateRequest(
        @Size(max = 2000, message = "Las notas no pueden exceder los 2000 caracteres")
        String notes,

        @Min(value = -100000, message = "El ajuste de puntos no puede ser menor a -100,000")
        @Max(value = 100000, message = "El ajuste de puntos no puede ser mayor a 100,000")
        Integer loyaltyPointsAdjustment,

        Boolean isBlocked
) {}
