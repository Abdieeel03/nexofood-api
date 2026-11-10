package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.modules.catalog.application.usecase.GetBasePriceUseCase;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetBasePriceService implements GetBasePriceUseCase {

    @Override
    public BigDecimal execute(Product product) {
        ProductPrice baseRule = executeRule(product);
        return baseRule != null ? baseRule.getPrice() : BigDecimal.ZERO;
    }

    @Override
    public ProductPrice executeRule(Product product) {
        if (product == null || product.getPrices() == null || product.getPrices().isEmpty()) {
            return null;
        }
        return product.getPrices().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsBase()))
                .findFirst()
                .orElse(product.getPrices().getFirst());
    }
}
