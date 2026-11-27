package lat.nexofood.api.modules.analytics.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.modules.analytics.domain.ExpenseCategory;
import lat.nexofood.api.modules.analytics.domain.ExpenseType;
import lat.nexofood.api.modules.analytics.infrastructure.repository.FinancialAnalyticsRepository;
import lat.nexofood.api.modules.analytics.infrastructure.repository.TenantExpenseRepository;
import lat.nexofood.api.modules.analytics.web.dto.response.FinancialSummaryResponse;
import lat.nexofood.api.modules.analytics.web.dto.response.ProductMarginResponse;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialDashboardServiceTest {

    @Mock
    private FinancialAnalyticsRepository financialAnalyticsRepository;

    @Mock
    private TenantExpenseRepository tenantExpenseRepository;

    @InjectMocks
    private FinancialDashboardServiceImpl dashboardService;

    @Test
    @DisplayName("Debe calcular correctamente Ganancia Bruta, Operativa, Neta, Márgenes y Markup con IGV 18% separado")
    void shouldCalculateAllFinancialMetricsAccurately() {
        UUID tenantId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 26);

        // Escenario: 1 producto vendido a S/ 25.00 con IGV 18% incluido (base S/ 21.19 + IGV S/ 3.81) y costo S/ 10.00
        FinancialAnalyticsRepository.OrderFinancialTotalsProjection orderTotals =
                new FinancialAnalyticsRepository.OrderFinancialTotalsProjection() {
                    @Override public Long getOrderCount() { return 1L; }
                    @Override public BigDecimal getNetSales() { return new BigDecimal("21.19"); }
                    @Override public BigDecimal getTaxCollected() { return new BigDecimal("3.81"); }
                    @Override public BigDecimal getDeliveryFees() { return new BigDecimal("5.00"); }
                };

        FinancialAnalyticsRepository.OrderItemsCostTotalsProjection itemTotals =
                new FinancialAnalyticsRepository.OrderItemsCostTotalsProjection() {
                    @Override public Long getUnitsSold() { return 1L; }
                    @Override public BigDecimal getCostOfGoodsSold() { return new BigDecimal("10.00"); }
                };

        TenantExpenseRepository.ExpenseTypeSummaryProjection opExpense =
                new TenantExpenseRepository.ExpenseTypeSummaryProjection() {
                    @Override public ExpenseType getType() { return ExpenseType.OPERATING; }
                    @Override public BigDecimal getTotalAmount() { return new BigDecimal("4.00"); }
                };
        TenantExpenseRepository.ExpenseTypeSummaryProjection nonOpExpense =
                new TenantExpenseRepository.ExpenseTypeSummaryProjection() {
                    @Override public ExpenseType getType() { return ExpenseType.NON_OPERATING; }
                    @Override public BigDecimal getTotalAmount() { return new BigDecimal("1.00"); }
                };
        TenantExpenseRepository.ExpenseTypeSummaryProjection taxExpense =
                new TenantExpenseRepository.ExpenseTypeSummaryProjection() {
                    @Override public ExpenseType getType() { return ExpenseType.INCOME_TAX; }
                    @Override public BigDecimal getTotalAmount() { return new BigDecimal("1.19"); }
                };

        TenantExpenseRepository.ExpenseCategorySummaryProjection catSummary =
                new TenantExpenseRepository.ExpenseCategorySummaryProjection() {
                    @Override public ExpenseCategory getCategory() { return ExpenseCategory.UTILITIES; }
                    @Override public ExpenseType getType() { return ExpenseType.OPERATING; }
                    @Override public BigDecimal getTotalAmount() { return new BigDecimal("4.00"); }
                };

        when(financialAnalyticsRepository.aggregateOrderFinancialTotals(
                eq(tenantId), eq(OrderStatus.CANCELADO), eq(PaymentStatus.APPROVED), any(), any(), eq(true)))
                .thenReturn(orderTotals);
        when(financialAnalyticsRepository.aggregateOrderItemsCostTotals(
                eq(tenantId), eq(OrderStatus.CANCELADO), eq(PaymentStatus.APPROVED), any(), any(), eq(true)))
                .thenReturn(itemTotals);
        when(tenantExpenseRepository.sumByTypeForTenantAndPeriod(tenantId, from, to))
                .thenReturn(List.of(opExpense, nonOpExpense, taxExpense));
        when(tenantExpenseRepository.sumByCategoryForTenantAndPeriod(tenantId, from, to))
                .thenReturn(List.of(catSummary));

        FinancialSummaryResponse summary = dashboardService.getFinancialSummary(tenantId, from, to, true);

        assertThat(summary.totalOrders()).isEqualTo(1L);
        assertThat(summary.totalUnitsSold()).isEqualTo(1L);
        assertThat(summary.grossSalesWithTax()).isEqualByComparingTo("25.00");
        assertThat(summary.netSales()).isEqualByComparingTo("21.19");
        assertThat(summary.taxCollected()).isEqualByComparingTo("3.81");
        assertThat(summary.deliveryFeesCollected()).isEqualByComparingTo("5.00");
        assertThat(summary.costOfGoodsSold()).isEqualByComparingTo("10.00");
        assertThat(summary.grossProfit()).isEqualByComparingTo("11.19");
        assertThat(summary.operatingExpenses()).isEqualByComparingTo("4.00");
        assertThat(summary.operatingProfit()).isEqualByComparingTo("7.19");
        assertThat(summary.nonOperatingExpenses()).isEqualByComparingTo("1.00");
        assertThat(summary.incomeTaxExpenses()).isEqualByComparingTo("1.19");
        assertThat(summary.netProfit()).isEqualByComparingTo("5.00");
        assertThat(summary.grossMarginPercentage()).isEqualByComparingTo("52.81");
        assertThat(summary.operatingMarginPercentage()).isEqualByComparingTo("33.93");
        assertThat(summary.netMarginPercentage()).isEqualByComparingTo("23.60");
        assertThat(summary.markupPercentage()).isEqualByComparingTo("111.90");
        assertThat(summary.expensesByCategory()).hasSize(1);
    }

    @Test
    @DisplayName("Debe manejar ventas y costos en cero sin lanzar ArithmeticException por división entre cero")
    void shouldHandleZeroSalesGracefully() {
        UUID tenantId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 26);

        when(financialAnalyticsRepository.aggregateOrderFinancialTotals(
                eq(tenantId), eq(OrderStatus.CANCELADO), eq(PaymentStatus.APPROVED), any(), any(), eq(true)))
                .thenReturn(null);
        when(financialAnalyticsRepository.aggregateOrderItemsCostTotals(
                eq(tenantId), eq(OrderStatus.CANCELADO), eq(PaymentStatus.APPROVED), any(), any(), eq(true)))
                .thenReturn(null);
        when(tenantExpenseRepository.sumByTypeForTenantAndPeriod(tenantId, from, to)).thenReturn(List.of());
        when(tenantExpenseRepository.sumByCategoryForTenantAndPeriod(tenantId, from, to)).thenReturn(List.of());

        FinancialSummaryResponse summary = dashboardService.getFinancialSummary(tenantId, from, to, true);

        assertThat(summary.netSales()).isEqualByComparingTo("0.00");
        assertThat(summary.grossProfit()).isEqualByComparingTo("0.00");
        assertThat(summary.grossMarginPercentage()).isEqualByComparingTo("0.00");
        assertThat(summary.markupPercentage()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Debe calcular margen y markup por producto correctamente")
    void shouldCalculateProductMargins() {
        UUID tenantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 26);

        FinancialAnalyticsRepository.ProductMarginProjection proj =
                new FinancialAnalyticsRepository.ProductMarginProjection() {
                    @Override public UUID getProductId() { return productId; }
                    @Override public String getProductName() { return "Hamburguesa Clásica"; }
                    @Override public Long getUnitsSold() { return 2L; }
                    @Override public BigDecimal getNetSales() { return new BigDecimal("42.38"); }
                    @Override public BigDecimal getTaxCollected() { return new BigDecimal("7.62"); }
                    @Override public BigDecimal getTotalCost() { return new BigDecimal("20.00"); }
                };

        when(financialAnalyticsRepository.aggregateProductMargins(
                eq(tenantId), eq(OrderStatus.CANCELADO), eq(PaymentStatus.APPROVED), any(), any(), eq(true)))
                .thenReturn(List.of(proj));

        List<ProductMarginResponse> result = dashboardService.getProductMargins(tenantId, from, to, true);

        assertThat(result).hasSize(1);
        ProductMarginResponse item = result.get(0);
        assertThat(item.productId()).isEqualTo(productId);
        assertThat(item.grossProfit()).isEqualByComparingTo("22.38");
        assertThat(item.grossMarginPercentage()).isEqualByComparingTo("52.81");
        assertThat(item.markupPercentage()).isEqualByComparingTo("111.90");
    }

    @Test
    @DisplayName("Debe rechazar rango de fechas inválido en el dashboard")
    void shouldRejectInvalidDateRange() {
        UUID tenantId = UUID.randomUUID();
        assertThatThrownBy(() -> dashboardService.getFinancialSummary(
                tenantId,
                LocalDate.of(2026, 11, 26),
                LocalDate.of(2026, 11, 1),
                true
        )).isInstanceOf(BadRequestException.class);
    }
}
