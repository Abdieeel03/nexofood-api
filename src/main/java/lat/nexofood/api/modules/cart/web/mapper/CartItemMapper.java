package lat.nexofood.api.modules.cart.web.mapper;

import lat.nexofood.api.modules.cart.domain.Cart;
import lat.nexofood.api.modules.cart.domain.CartItem;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemRequest;
import lat.nexofood.api.modules.cart.web.dto.response.CartItemResponse;
import lat.nexofood.api.modules.catalog.application.service.CalculateProductPriceService;
import lat.nexofood.api.modules.catalog.application.service.GetBasePriceService;
import lat.nexofood.api.modules.catalog.application.service.GetEffectivePriceRuleService;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingService;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingServiceImpl;
import lat.nexofood.api.modules.catalog.domain.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CartItemMapper {

    private final ProductPricingService pricingService;

    public CartItemMapper() {
        this.pricingService = createDefaultPricingService();
    }

    @Autowired
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
        Product product = item.getProduct();
        boolean available = isProductAvailable(product);
        return toResponse(item, null, false, available);
    }

    public CartItemResponse toResponse(
            CartItem item,
            BigDecimal previousUnitPrice,
            boolean priceChanged,
            boolean productAvailable
    ) {
        if (item == null) {
            return null;
        }
        Product product = item.getProduct();
        BigDecimal subtotal = item.getSubtotal() != null ? item.getSubtotal() : item.calculateSubtotal();

        return CartItemResponse.builder()
                .id(item.getId())
                .productId(product != null ? product.getId() : null)
                .productName(product != null ? product.getName() : null)
                .productImageUrl(product != null ? product.getImageUrl() : null)
                .unitPrice(item.getUnitPrice())
                .previousUnitPrice(previousUnitPrice)
                .priceChanged(priceChanged)
                .productAvailable(productAvailable)
                .quantity(item.getQuantity())
                .subtotal(subtotal)
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
                .tenant(cart != null && cart.getTenant() != null ? cart.getTenant() : product.getTenant())
                .product(product)
                .quantity(request.quantity())
                .unitPrice(unitPrice)
                .subtotal(subtotal)
                .notes(request.notes())
                .build();
    }

    private boolean isProductAvailable(Product product) {
        if (product == null) {
            return false;
        }
        boolean productActive = product.getIsAvailable() == null || Boolean.TRUE.equals(product.getIsAvailable());
        boolean categoryActive = product.getCategory() == null
                || product.getCategory().getIsActive() == null
                || Boolean.TRUE.equals(product.getCategory().getIsActive());
        return productActive && categoryActive;
    }
}
