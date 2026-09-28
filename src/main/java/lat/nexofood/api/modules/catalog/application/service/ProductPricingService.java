package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface ProductPricingService {

    BigDecimal calculateCurrentPrice(Product product);

    BigDecimal calculateCurrentPrice(Product product, LocalDateTime dateTime);

    BigDecimal getBasePrice(Product product);

    ProductPrice getEffectivePriceRule(Product product, LocalDateTime dateTime);

    ProductPrice getBasePriceRule(Product product);

    boolean isPriceApplicable(ProductPrice price, LocalDateTime dateTime);
}
