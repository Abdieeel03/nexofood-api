package lat.nexofood.api.modules.catalog.application.usecase;

import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;

import java.math.BigDecimal;

public interface GetBasePriceUseCase {

    BigDecimal execute(Product product);

    ProductPrice executeRule(Product product);
}
