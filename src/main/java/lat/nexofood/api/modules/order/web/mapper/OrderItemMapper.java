package lat.nexofood.api.modules.order.web.mapper;

import lat.nexofood.api.modules.catalog.application.service.CalculateProductPriceService;
import lat.nexofood.api.modules.catalog.application.service.GetBasePriceService;
import lat.nexofood.api.modules.catalog.application.service.GetEffectivePriceRuleService;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingService;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingServiceImpl;
import lat.nexofood.api.modules.catalog.application.service.TaxCalculationResult;
import lat.nexofood.api.modules.catalog.application.service.TaxCalculationService;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderItem;
import lat.nexofood.api.modules.order.web.dto.request.OrderItemRequest;
import lat.nexofood.api.modules.order.web.dto.response.OrderItemResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class OrderItemMapper {

    private final ProductPricingService pricingService;
    private final TaxCalculationService taxCalculationService;

    public OrderItemMapper() {
        this(createDefaultPricingService(), new TaxCalculationService());
    }

    public OrderItemMapper(ProductPricingService pricingService) {
        this(pricingService, new TaxCalculationService());
    }

    public OrderItemMapper(ProductPricingService pricingService,
                            TaxCalculationService taxCalculationService) {
        this.pricingService = pricingService != null ? pricingService : createDefaultPricingService();
        this.taxCalculationService = taxCalculationService != null ? taxCalculationService : new TaxCalculationService();
    }

    private static ProductPricingService createDefaultPricingService() {
        GetBasePriceService basePriceService = new GetBasePriceService();
        GetEffectivePriceRuleService effectiveRuleService = new GetEffectivePriceRuleService(basePriceService);
        CalculateProductPriceService calculatePriceService = new CalculateProductPriceService(effectiveRuleService);
        return new ProductPricingServiceImpl(calculatePriceService, basePriceService, effectiveRuleService);
    }

    public OrderItemResponse toResponse(OrderItem item) {
        if (item == null) {
            return null;
        }
        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .productName(item.getProductName())
                .taxId(item.getTax() != null ? item.getTax().getId() : null)
                .taxRate(item.getTaxRate())
                .taxAmount(item.getTaxAmount())
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .subtotal(item.getSubtotal())
                .notes(item.getNotes())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    public OrderItem toEntity(OrderItemRequest request, Product product, Order order) {
        if (request == null || product == null) {
            return null;
        }
        BigDecimal sellingPrice = pricingService.calculateCurrentPrice(product);
        TaxCalculationResult calc = taxCalculationService.calculate(sellingPrice, product.getTax(), request.quantity());

        return toEntity(request, product, order, calc);
    }

    public OrderItem toEntity(OrderItemRequest request, Product product, Order order,
                              TaxCalculationResult calc) {
        if (request == null || product == null) {
            return null;
        }

        lat.nexofood.api.modules.catalog.domain.ProductPrice effectivePrice = null;
        if (pricingService != null) {
            effectivePrice = pricingService.getEffectivePriceRule(product, java.time.LocalDateTime.now());
            if (effectivePrice == null) {
                effectivePrice = pricingService.getBasePriceRule(product);
            }
        }

        return OrderItem.builder()
                .order(order)
                .tenant(order != null ? order.getTenant() : product.getTenant())
                .product(product)
                .price(effectivePrice)
                .productName(product.getName())
                .tax(calc != null ? calc.tax() : product.getTax())
                .taxRate(calc != null && calc.taxRate() != null ? calc.taxRate() : BigDecimal.ZERO)
                .taxAmount(calc != null && calc.taxAmount() != null ? calc.taxAmount() : BigDecimal.ZERO)
                .unitPrice(calc != null ? calc.baseUnitPrice() : pricingService.calculateCurrentPrice(product))
                .quantity(request.quantity())
                .subtotal(calc != null ? calc.subtotal() : null)
                .notes(request.notes())
                .build();
    }
}
