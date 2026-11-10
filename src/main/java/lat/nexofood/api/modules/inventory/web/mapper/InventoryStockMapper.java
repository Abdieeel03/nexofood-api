package lat.nexofood.api.modules.inventory.web.mapper;

import lat.nexofood.api.modules.inventory.domain.InventoryItem;
import lat.nexofood.api.modules.inventory.domain.InventoryStock;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryStockRequest;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryStockResponse;
import lat.nexofood.api.modules.store.domain.Tenant;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class InventoryStockMapper {

    public InventoryStockResponse toResponse(InventoryStock stock) {
        if (stock == null) {
            return null;
        }
        InventoryItem item = stock.getItem();
        return InventoryStockResponse.builder()
                .id(stock.getId())
                .tenantId(stock.getTenant() != null ? stock.getTenant().getId() : null)
                .itemId(item != null ? item.getId() : null)
                .itemName(item != null ? item.getName() : null)
                .itemSku(item != null ? item.getSku() : null)
                .unit(stock.getUnit())
                .quantity(stock.getQuantity())
                .reservedQuantity(stock.getReservedQuantity())
                .availableQuantity(stock.getAvailableQuantity())
                .minimumStock(stock.getMinimumStock())
                .lowStock(stock.isLowStock())
                .location(stock.getLocation())
                .isActive(stock.getIsActive())
                .createdAt(stock.getCreatedAt())
                .updatedAt(stock.getUpdatedAt())
                .build();
    }

    public InventoryStock toEntity(InventoryStockRequest request, Tenant tenant, InventoryItem item) {
        if (request == null) {
            return null;
        }
        BigDecimal zero = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        BigDecimal minimumStock = request.minimumStock() != null
                ? request.minimumStock().setScale(3, RoundingMode.HALF_UP)
                : zero;

        return InventoryStock.builder()
                .tenant(tenant)
                .item(item)
                .quantity(zero)
                .reservedQuantity(zero)
                .minimumStock(minimumStock)
                .location(request.location() != null && !request.location().isBlank() ? request.location().trim() : null)
                .isActive(request.isActive() != null ? request.isActive() : true)
                .build();
    }
}
