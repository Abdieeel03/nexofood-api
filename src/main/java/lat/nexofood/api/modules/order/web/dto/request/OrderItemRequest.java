package lat.nexofood.api.modules.order.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.UUID;

@Builder
public record OrderItemRequest(
        @NotNull(message = "El ID del producto es obligatorio")
        UUID productId,

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad mínima es 1")
        @Max(value = 999, message = "La cantidad máxima es 999")
        Integer quantity,

        @Size(max = 500, message = "Las notas del ítem no pueden superar los 500 caracteres")
        String notes
) {}
