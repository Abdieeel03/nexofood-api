package lat.nexofood.api.modules.inventory.web.dto.response;

import lat.nexofood.api.modules.inventory.domain.InventoryUnit;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record InventoryItemResponse(
        UUID id,
        UUID tenantId,
        String name,
        String sku,
        String description,
        String category,
        InventoryUnit unit,
        BigDecimal costPrice,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
