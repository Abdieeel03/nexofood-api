package lat.nexofood.api.modules.cart.web.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CartItemUpdateRequest(
        @Min(value = 1, message = "La cantidad mínima es 1")
        Integer quantity,

        @Size(max = 500, message = "Las notas del ítem no pueden superar los 500 caracteres")
        String notes
) {}
