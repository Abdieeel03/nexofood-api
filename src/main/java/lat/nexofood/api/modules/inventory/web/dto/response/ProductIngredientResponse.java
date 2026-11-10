package lat.nexofood.api.modules.inventory.web.dto.response;

import lat.nexofood.api.modules.inventory.domain.InventoryUnit;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record ProductIngredientResponse(
        UUID id,
        UUID tenantId,
        UUID productId,
        String productName,
        UUID inventoryItemId,
        String inventoryItemName,
        String inventoryItemSku,
        InventoryUnit unit,
        BigDecimal quantity,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
