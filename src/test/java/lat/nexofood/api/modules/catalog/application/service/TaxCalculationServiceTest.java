package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.modules.catalog.domain.Tax;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaxCalculationServiceTest {

    private TaxCalculationService taxCalculationService;

    @BeforeEach
    void setUp() {
        taxCalculationService = new TaxCalculationService();
    }

    @Test
    @DisplayName("Caso 6A: Impuesto INCLUIDO en el precio (is_inclusive = true, IGV 18%)")
    void testInclusiveTaxCalculation() {
        Tax igvTax = Tax.builder()
                .id(UUID.randomUUID())
                .name("IGV 18%")
                .rate(new BigDecimal("18.00"))
                .isInclusive(true)
                .isActive(true)
                .build();

        // Producto con precio de venta 118.00 soles (incluye IGV), cantidad 1
        TaxCalculationResult result1 = taxCalculationService.calculate(new BigDecimal("118.00"), igvTax, 1);
        assertEquals(new BigDecimal("100.00"), result1.baseUnitPrice());
        assertEquals(new BigDecimal("100.00"), result1.subtotal());
        assertEquals(new BigDecimal("18.00"), result1.taxRate());
        assertEquals(new BigDecimal("18.00"), result1.taxAmount());
        assertEquals(new BigDecimal("118.00"), result1.subtotal().add(result1.taxAmount()));

        // Producto con precio de venta 20.00 soles (incluye IGV), cantidad 2 -> total venta = 40.00
        // base = 20 / 1.18 = 16.95, subtotal = 16.95 * 2 = 33.90, tax = 40.00 - 33.90 = 6.10
        TaxCalculationResult result2 = taxCalculationService.calculate(new BigDecimal("20.00"), igvTax, 2);
        assertEquals(new BigDecimal("16.95"), result2.baseUnitPrice());
        assertEquals(new BigDecimal("33.90"), result2.subtotal());
        assertEquals(new BigDecimal("18.00"), result2.taxRate());
        assertEquals(new BigDecimal("6.10"), result2.taxAmount());
        assertEquals(new BigDecimal("40.00"), result2.subtotal().add(result2.taxAmount()));
    }

    @Test
    @DisplayName("Caso 6B: Impuesto NO INCLUIDO en el precio (is_inclusive = false, 10% propina/servicio)")
    void testExclusiveTaxCalculation() {
        Tax extraTax = Tax.builder()
                .id(UUID.randomUUID())
                .name("Servicio 10%")
                .rate(new BigDecimal("10.00"))
                .isInclusive(false)
                .isActive(true)
                .build();

        // Producto con precio neto de 50.00 soles, cantidad 3 -> subtotal base = 150.00, tax = 15.00
        TaxCalculationResult result = taxCalculationService.calculate(new BigDecimal("50.00"), extraTax, 3);
        assertEquals(new BigDecimal("50.00"), result.baseUnitPrice());
        assertEquals(new BigDecimal("150.00"), result.subtotal());
        assertEquals(new BigDecimal("10.00"), result.taxRate());
        assertEquals(new BigDecimal("15.00"), result.taxAmount());
        assertEquals(new BigDecimal("165.00"), result.subtotal().add(result.taxAmount()));
    }

    @Test
    @DisplayName("Caso 5A: Producto sin impuesto asociado (tax = null)")
    void testNullTaxCalculation() {
        TaxCalculationResult result = taxCalculationService.calculate(new BigDecimal("25.00"), null, 2);
        assertEquals(new BigDecimal("25.00"), result.baseUnitPrice());
        assertEquals(new BigDecimal("50.00"), result.subtotal());
        assertEquals(new BigDecimal("0.00"), result.taxRate());
        assertEquals(new BigDecimal("0.00"), result.taxAmount());
        assertNull(result.tax());
    }

    @Test
    @DisplayName("Caso 5B: Producto con impuesto INACTIVO (isActive = false)")
    void testInactiveTaxCalculation() {
        Tax inactiveTax = Tax.builder()
                .id(UUID.randomUUID())
                .name("Impuesto Antiguo")
                .rate(new BigDecimal("18.00"))
                .isInclusive(true)
                .isActive(false)
                .build();

        TaxCalculationResult result = taxCalculationService.calculate(new BigDecimal("30.00"), inactiveTax, 1);
        assertEquals(new BigDecimal("30.00"), result.baseUnitPrice());
        assertEquals(new BigDecimal("30.00"), result.subtotal());
        assertEquals(new BigDecimal("0.00"), result.taxRate());
        assertEquals(new BigDecimal("0.00"), result.taxAmount());
        assertNull(result.tax());
    }
}
