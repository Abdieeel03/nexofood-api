package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.modules.catalog.domain.Tax;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record TaxCalculationResult(
        BigDecimal baseUnitPrice,
        BigDecimal subtotal,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        Tax tax
) {}
