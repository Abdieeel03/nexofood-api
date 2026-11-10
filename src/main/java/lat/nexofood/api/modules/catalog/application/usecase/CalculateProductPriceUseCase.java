package lat.nexofood.api.modules.catalog.application.usecase;

import lat.nexofood.api.modules.catalog.domain.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface CalculateProductPriceUseCase {

    BigDecimal execute(Product product);

    BigDecimal execute(Product product, LocalDateTime dateTime);
}
