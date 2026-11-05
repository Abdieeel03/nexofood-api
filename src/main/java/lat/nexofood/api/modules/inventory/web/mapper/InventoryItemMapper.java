package lat.nexofood.api.modules.inventory.web.mapper;

import lat.nexofood.api.modules.inventory.domain.InventoryItem;
import lat.nexofood.api.modules.inventory.domain.InventoryUnit;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryItemRequest;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryItemResponse;
import lat.nexofood.api.modules.store.domain.Tenant;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class InventoryItemMapper {

    public InventoryItemResponse toResponse(InventoryItem item) {
        if (item == null) {
            return null;
        }
        return InventoryItemResponse.builder()
                .id(item.getId())
                .tenantId(item.getTenant() != null ? item.getTenant().getId() : null)
                .name(item.getName())
                .sku(item.getSku())
                .description(item.getDescription())
                .category(item.getCategory())
                .unit(item.getUnit())
                .costPrice(item.getCostPrice())
                .isActive(item.getIsActive())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    public InventoryItem toEntity(InventoryItemRequest request, Tenant tenant) {
        if (request == null) {
            return null;
        }
        BigDecimal costPrice = request.costPrice() != null
                ? request.costPrice().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return InventoryItem.builder()
                .tenant(tenant)
                .name(request.name() != null ? request.name().trim() : null)
                .sku(request.sku() != null && !request.sku().isBlank() ? request.sku().trim() : null)
                .description(request.description())
                .category(request.category() != null && !request.category().isBlank() ? request.category().trim() : null)
                .unit(request.unit() != null ? request.unit() : InventoryUnit.UNIT)
                .costPrice(costPrice)
                .isActive(request.isActive() != null ? request.isActive() : true)
                .build();
    }
}
