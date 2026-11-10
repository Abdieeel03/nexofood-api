package lat.nexofood.api.modules.inventory.web.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementReason;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementType;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record InventoryMovementRequest(
        @NotNull(message = "El ID del registro de stock (stockId) es obligatorio")
        UUID stockId,

        @NotNull(message = "El tipo de movimiento (movementType) es obligatorio")
        InventoryMovementType movementType,

        @NotNull(message = "El motivo del movimiento (reason) es obligatorio")
        InventoryMovementReason reason,

        @NotNull(message = "La cantidad del movimiento es obligatoria")
        @DecimalMin(value = "0.001", message = "La cantidad del movimiento debe ser mayor a cero")
        @DecimalMax(value = "999999.999", message = "La cantidad del movimiento no puede superar 999,999.999")
        BigDecimal quantity,

        @NotBlank(message = "La justificación del movimiento (reasonDetails) es obligatoria")
        @Size(max = 1000, message = "Los detalles del motivo no pueden superar los 1000 caracteres")
        String reasonDetails
) {
}
