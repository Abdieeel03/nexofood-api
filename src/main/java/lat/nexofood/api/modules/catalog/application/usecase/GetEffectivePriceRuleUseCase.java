package lat.nexofood.api.modules.catalog.application.usecase;

import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;

import java.time.LocalDateTime;

public interface GetEffectivePriceRuleUseCase {

    ProductPrice execute(Product product);

    ProductPrice execute(Product product, LocalDateTime dateTime);

    boolean isPriceApplicable(ProductPrice price, LocalDateTime dateTime);
}
