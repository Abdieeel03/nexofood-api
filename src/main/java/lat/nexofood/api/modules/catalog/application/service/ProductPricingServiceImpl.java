package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.modules.catalog.application.usecase.CalculateProductPriceUseCase;
import lat.nexofood.api.modules.catalog.application.usecase.GetBasePriceUseCase;
import lat.nexofood.api.modules.catalog.application.usecase.GetEffectivePriceRuleUseCase;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductPricingServiceImpl implements ProductPricingService {

    private final CalculateProductPriceUseCase calculateProductPriceUseCase;
    private final GetBasePriceUseCase getBasePriceUseCase;
    private final GetEffectivePriceRuleUseCase getEffectivePriceRuleUseCase;

    @Override
    public BigDecimal calculateCurrentPrice(Product product) {
        return calculateProductPriceUseCase.execute(product);
    }

    @Override
    public BigDecimal calculateCurrentPrice(Product product, LocalDateTime dateTime) {
        return calculateProductPriceUseCase.execute(product, dateTime);
    }

    @Override
    public BigDecimal getBasePrice(Product product) {
        return getBasePriceUseCase.execute(product);
    }

    @Override
    public ProductPrice getEffectivePriceRule(Product product, LocalDateTime dateTime) {
        return getEffectivePriceRuleUseCase.execute(product, dateTime);
    }

    @Override
    public ProductPrice getBasePriceRule(Product product) {
        return getBasePriceUseCase.executeRule(product);
    }

    @Override
    public boolean isPriceApplicable(ProductPrice price, LocalDateTime dateTime) {
        return getEffectivePriceRuleUseCase.isPriceApplicable(price, dateTime);
    }
}
