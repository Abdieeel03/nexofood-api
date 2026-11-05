package lat.nexofood.api.modules.inventory.web.dto.response;

import lat.nexofood.api.modules.inventory.domain.InventoryMovementReason;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementType;
import lat.nexofood.api.modules.inventory.domain.InventoryUnit;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record InventoryMovementResponse(
        UUID id,
        UUID tenantId,
        UUID stockId,
        UUID itemId,
        String itemName,
        String itemSku,
        InventoryUnit unit,
        InventoryMovementType movementType,
        InventoryMovementReason reason,
        BigDecimal quantity,
        BigDecimal previousQuantity,
        BigDecimal newQuantity,
        UUID performedById,
        String performedByName,
        String reasonDetails,
        UUID referenceId,
        String referenceType,
        OffsetDateTime createdAt
) {
}
