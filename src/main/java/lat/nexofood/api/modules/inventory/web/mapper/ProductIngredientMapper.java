package lat.nexofood.api.modules.inventory.web.mapper;

import lat.nexofood.api.modules.inventory.domain.InventoryItem;
import lat.nexofood.api.modules.inventory.domain.ProductIngredient;
import lat.nexofood.api.modules.inventory.web.dto.response.ProductIngredientResponse;
import org.springframework.stereotype.Component;

@Component
public class ProductIngredientMapper {

    public ProductIngredientResponse toResponse(ProductIngredient ingredient) {
        if (ingredient == null) {
            return null;
        }
        InventoryItem item = ingredient.getInventoryItem();
        return ProductIngredientResponse.builder()
                .id(ingredient.getId())
                .tenantId(ingredient.getTenant() != null ? ingredient.getTenant().getId() : null)
                .productId(ingredient.getProduct() != null ? ingredient.getProduct().getId() : null)
                .productName(ingredient.getProduct() != null ? ingredient.getProduct().getName() : null)
                .inventoryItemId(item != null ? item.getId() : null)
                .inventoryItemName(item != null ? item.getName() : null)
                .inventoryItemSku(item != null ? item.getSku() : null)
                .unit(item != null ? item.getUnit() : null)
                .quantity(ingredient.getQuantity())
                .createdAt(ingredient.getCreatedAt())
                .updatedAt(ingredient.getUpdatedAt())
                .build();
    }
}
