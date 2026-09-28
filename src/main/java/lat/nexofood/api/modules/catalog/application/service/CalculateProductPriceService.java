package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.modules.catalog.application.usecase.CalculateProductPriceUseCase;
import lat.nexofood.api.modules.catalog.application.usecase.GetEffectivePriceRuleUseCase;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalculateProductPriceService implements CalculateProductPriceUseCase {

    private final GetEffectivePriceRuleUseCase getEffectivePriceRuleUseCase;

    @Override
    public BigDecimal execute(Product product) {
        return execute(product, LocalDateTime.now());
    }

    @Override
    public BigDecimal execute(Product product, LocalDateTime dateTime) {
        ProductPrice rule = getEffectivePriceRuleUseCase.execute(product, dateTime);
        return rule != null ? rule.getPrice() : BigDecimal.ZERO;
    }
}
