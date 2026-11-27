package lat.nexofood.api.modules.analytics.web.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Builder
public record FinancialSummaryResponse(
        UUID tenantId,
        LocalDate fromDate,
        LocalDate toDate,
        boolean paidOnly,
        long totalOrders,
        long totalUnitsSold,
        BigDecimal grossSalesWithTax,
        BigDecimal netSales,
        BigDecimal taxCollected,
        BigDecimal deliveryFeesCollected,
        BigDecimal costOfGoodsSold,
        BigDecimal grossProfit,
        BigDecimal operatingExpenses,
        BigDecimal operatingProfit,
        BigDecimal nonOperatingExpenses,
        BigDecimal incomeTaxExpenses,
        BigDecimal netProfit,
        BigDecimal grossMarginPercentage,
        BigDecimal operatingMarginPercentage,
        BigDecimal netMarginPercentage,
        BigDecimal markupPercentage,
        List<ExpenseCategoryBreakdownResponse> expensesByCategory
) {}
