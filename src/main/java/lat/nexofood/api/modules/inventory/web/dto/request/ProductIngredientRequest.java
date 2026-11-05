package lat.nexofood.api.modules.inventory.web.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record ProductIngredientRequest(
        @NotNull(message = "El ID del insumo (inventoryItemId) es obligatorio")
        UUID inventoryItemId,

        @NotNull(message = "La cantidad requerida del insumo es obligatoria")
        @DecimalMin(value = "0.001", message = "La cantidad del ingrediente debe ser mayor a cero")
        BigDecimal quantity
) {
}
