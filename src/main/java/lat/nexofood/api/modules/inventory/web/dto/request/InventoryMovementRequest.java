package lat.nexofood.api.modules.inventory.web.dto.request;

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
        BigDecimal quantity,

        @NotBlank(message = "La justificación del movimiento (reasonDetails) es obligatoria")
        String reasonDetails,

        UUID referenceId,

        @Size(max = 50, message = "El tipo de referencia no puede exceder los 50 caracteres")
        String referenceType
) {
}
