package lat.nexofood.api.modules.inventory.web.mapper;

import lat.nexofood.api.modules.inventory.domain.InventoryItem;
import lat.nexofood.api.modules.inventory.domain.InventoryMovement;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryMovementResponse;
import org.springframework.stereotype.Component;

@Component
public class InventoryMovementMapper {

    public InventoryMovementResponse toResponse(InventoryMovement movement) {
        if (movement == null) {
            return null;
        }
        InventoryItem item = movement.getItem();
        return InventoryMovementResponse.builder()
                .id(movement.getId())
                .tenantId(movement.getTenant() != null ? movement.getTenant().getId() : null)
                .stockId(movement.getStock() != null ? movement.getStock().getId() : null)
                .itemId(item != null ? item.getId() : null)
                .itemName(item != null ? item.getName() : null)
                .itemSku(item != null ? item.getSku() : null)
                .unit(item != null ? item.getUnit() : null)
                .movementType(movement.getMovementType())
                .reason(movement.getReason())
                .quantity(movement.getQuantity())
                .previousQuantity(movement.getPreviousQuantity())
                .newQuantity(movement.getNewQuantity())
                .performedById(movement.getPerformedBy() != null ? movement.getPerformedBy().getId() : null)
                .performedByName(movement.getPerformedByName())
                .reasonDetails(movement.getReasonDetails())
                .createdAt(movement.getCreatedAt())
                .build();
    }
}
