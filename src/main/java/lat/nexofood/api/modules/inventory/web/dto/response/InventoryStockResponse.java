package lat.nexofood.api.modules.inventory.web.dto.response;

import lat.nexofood.api.modules.inventory.domain.InventoryUnit;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record InventoryStockResponse(
        UUID id,
        UUID tenantId,
        UUID itemId,
        String itemName,
        String itemSku,
        InventoryUnit unit,
        BigDecimal quantity,
        BigDecimal reservedQuantity,
        BigDecimal availableQuantity,
        BigDecimal minimumStock,
        Boolean lowStock,
        String location,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
