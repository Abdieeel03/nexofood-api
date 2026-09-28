package lat.nexofood.api.modules.cart.web.mapper;

import lat.nexofood.api.modules.catalog.application.service.CalculateProductPriceService;
import lat.nexofood.api.modules.catalog.application.service.GetBasePriceService;
import lat.nexofood.api.modules.catalog.application.service.GetEffectivePriceRuleService;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingService;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingServiceImpl;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.cart.domain.Cart;
import lat.nexofood.api.modules.cart.domain.CartItem;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemRequest;
import lat.nexofood.api.modules.cart.web.dto.response.CartItemResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CartItemMapper {

    private final ProductPricingService pricingService;

    public CartItemMapper() {
        this.pricingService = createDefaultPricingService();
    }

    public CartItemMapper(ProductPricingService pricingService) {
        this.pricingService = pricingService != null ? pricingService : createDefaultPricingService();
    }

    private static ProductPricingService createDefaultPricingService() {
        GetBasePriceService basePriceService = new GetBasePriceService();
        GetEffectivePriceRuleService effectiveRuleService = new GetEffectivePriceRuleService(basePriceService);
        CalculateProductPriceService calculatePriceService = new CalculateProductPriceService(effectiveRuleService);
        return new ProductPricingServiceImpl(calculatePriceService, basePriceService, effectiveRuleService);
    }

    public CartItemResponse toResponse(CartItem item) {
        if (item == null) {
            return null;
        }
        return CartItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .productName(item.getProduct() != null ? item.getProduct().getName() : null)
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .subtotal(item.getSubtotal())
                .notes(item.getNotes())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    public CartItem toEntity(CartItemRequest request, Product product, Cart cart) {
        if (request == null || product == null) {
            return null;
        }
        BigDecimal unitPrice = pricingService.calculateCurrentPrice(product);
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));

        return CartItem.builder()
                .cart(cart)
                .product(product)
                .quantity(request.quantity())
                .unitPrice(unitPrice)
                .subtotal(subtotal)
                .notes(request.notes())
                .build();
    }
}
