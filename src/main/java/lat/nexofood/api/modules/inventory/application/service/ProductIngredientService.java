package lat.nexofood.api.modules.inventory.application.service;

import lat.nexofood.api.modules.inventory.web.dto.request.ProductIngredientRequest;
import lat.nexofood.api.modules.inventory.web.dto.response.ProductIngredientResponse;
import lat.nexofood.api.modules.order.domain.Order;

import java.util.List;
import java.util.UUID;

public interface ProductIngredientService {

    List<ProductIngredientResponse> getRecipeByProduct(UUID tenantId, UUID productId);

    ProductIngredientResponse addOrUpdateIngredient(
            UUID tenantId,
            UUID productId,
            ProductIngredientRequest request
    );

    void removeIngredient(UUID tenantId, UUID productId, UUID ingredientId);

    void consumeStockForOrder(Order order);

    void restoreStockForCanceledOrder(Order order);
}
