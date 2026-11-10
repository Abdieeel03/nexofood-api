package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.modules.catalog.domain.Tax;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class TaxCalculationService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public TaxCalculationResult calculate(BigDecimal sellingPrice, Tax tax, int quantity) {
        if (sellingPrice == null) {
            sellingPrice = BigDecimal.ZERO;
        }

        BigDecimal qty = BigDecimal.valueOf(Math.max(quantity, 1));

        // Caso sin impuesto, impuesto inactivo, o tasa 0
        if (tax == null || !Boolean.TRUE.equals(tax.getIsActive()) || tax.getRate() == null
                || tax.getRate().compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal baseUnitPrice = sellingPrice.setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotal = baseUnitPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP);
            return TaxCalculationResult.builder()
                    .baseUnitPrice(baseUnitPrice)
                    .subtotal(subtotal)
                    .taxRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .taxAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .tax(tax != null && Boolean.TRUE.equals(tax.getIsActive()) ? tax : null)
                    .build();
        }

        BigDecimal taxRate = tax.getRate().setScale(2, RoundingMode.HALF_UP);

        if (Boolean.TRUE.equals(tax.getIsInclusive())) {
            // Impuesto INCLUIDO en el precio de venta
            // base = precio / (1 + rate / 100)
            BigDecimal factor = BigDecimal.ONE.add(taxRate.divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP));
            BigDecimal baseUnitPrice = sellingPrice.divide(factor, 2, RoundingMode.HALF_UP);
            BigDecimal subtotal = baseUnitPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP);

            BigDecimal totalSellingPrice = sellingPrice.setScale(2, RoundingMode.HALF_UP).multiply(qty);
            BigDecimal taxAmount = totalSellingPrice.subtract(subtotal);

            return TaxCalculationResult.builder()
                    .baseUnitPrice(baseUnitPrice)
                    .subtotal(subtotal)
                    .taxRate(taxRate)
                    .taxAmount(taxAmount)
                    .tax(tax)
                    .build();
        } else {
            // Impuesto NO INCLUIDO en el precio de venta (se suma sobre la base)
            BigDecimal baseUnitPrice = sellingPrice.setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotal = baseUnitPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxAmount = subtotal.multiply(taxRate).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

            return TaxCalculationResult.builder()
                    .baseUnitPrice(baseUnitPrice)
                    .subtotal(subtotal)
                    .taxRate(taxRate)
                    .taxAmount(taxAmount)
                    .tax(tax)
                    .build();
        }
    }
}
