package lat.nexofood.api.modules.analytics.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.modules.analytics.domain.ExpenseType;
import lat.nexofood.api.modules.analytics.infrastructure.repository.FinancialAnalyticsRepository;
import lat.nexofood.api.modules.analytics.infrastructure.repository.TenantExpenseRepository;
import lat.nexofood.api.modules.analytics.web.dto.response.ExpenseCategoryBreakdownResponse;
import lat.nexofood.api.modules.analytics.web.dto.response.FinancialSummaryResponse;
import lat.nexofood.api.modules.analytics.web.dto.response.ProductMarginResponse;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinancialDashboardServiceImpl implements FinancialDashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final FinancialAnalyticsRepository financialAnalyticsRepository;
    private final TenantExpenseRepository tenantExpenseRepository;

    @Override
    @Transactional(readOnly = true)
    public FinancialSummaryResponse getFinancialSummary(
            UUID tenantId,
            LocalDate fromDate,
            LocalDate toDate,
            boolean paidOnly
    ) {
        DateRange range = resolveDateRange(fromDate, toDate);

        FinancialAnalyticsRepository.OrderFinancialTotalsProjection orderTotals =
                financialAnalyticsRepository.aggregateOrderFinancialTotals(
                        tenantId,
                        OrderStatus.CANCELADO,
                        PaymentStatus.APPROVED,
                        range.fromDateTime(),
                        range.toDateTimeExclusive(),
                        paidOnly
                );

        FinancialAnalyticsRepository.OrderItemsCostTotalsProjection itemTotals =
                financialAnalyticsRepository.aggregateOrderItemsCostTotals(
                        tenantId,
                        OrderStatus.CANCELADO,
                        PaymentStatus.APPROVED,
                        range.fromDateTime(),
                        range.toDateTimeExclusive(),
                        paidOnly
                );

        long totalOrders = orderTotals != null && orderTotals.getOrderCount() != null ? orderTotals.getOrderCount() : 0L;
        long totalUnitsSold = itemTotals != null && itemTotals.getUnitsSold() != null ? itemTotals.getUnitsSold() : 0L;

        BigDecimal netSales = scale2(orderTotals != null ? orderTotals.getNetSales() : null);
        BigDecimal taxCollected = scale2(orderTotals != null ? orderTotals.getTaxCollected() : null);
        BigDecimal deliveryFeesCollected = scale2(orderTotals != null ? orderTotals.getDeliveryFees() : null);
        BigDecimal grossSalesWithTax = netSales.add(taxCollected).setScale(2, RoundingMode.HALF_UP);
        BigDecimal costOfGoodsSold = scale2(itemTotals != null ? itemTotals.getCostOfGoodsSold() : null);

        Map<ExpenseType, BigDecimal> expensesByType = new EnumMap<>(ExpenseType.class);
        for (TenantExpenseRepository.ExpenseTypeSummaryProjection row :
                tenantExpenseRepository.sumByTypeForTenantAndPeriod(tenantId, range.fromDate(), range.toDate())) {
            if (row.getType() != null) {
                expensesByType.put(row.getType(), scale2(row.getTotalAmount()));
            }
        }

        BigDecimal operatingExpenses = expensesByType.getOrDefault(ExpenseType.OPERATING, ZERO);
        BigDecimal nonOperatingExpenses = expensesByType.getOrDefault(ExpenseType.NON_OPERATING, ZERO);
        BigDecimal incomeTaxExpenses = expensesByType.getOrDefault(ExpenseType.INCOME_TAX, ZERO);

        BigDecimal grossProfit = netSales.subtract(costOfGoodsSold).setScale(2, RoundingMode.HALF_UP);
        BigDecimal operatingProfit = grossProfit.subtract(operatingExpenses).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netProfit = operatingProfit
                .subtract(nonOperatingExpenses)
                .subtract(incomeTaxExpenses)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal grossMarginPercentage = calculatePercentage(grossProfit, netSales);
        BigDecimal operatingMarginPercentage = calculatePercentage(operatingProfit, netSales);
        BigDecimal netMarginPercentage = calculatePercentage(netProfit, netSales);
        BigDecimal markupPercentage = calculatePercentage(grossProfit, costOfGoodsSold);

        List<ExpenseCategoryBreakdownResponse> categoryBreakdown = tenantExpenseRepository
                .sumByCategoryForTenantAndPeriod(tenantId, range.fromDate(), range.toDate())
                .stream()
                .map(row -> ExpenseCategoryBreakdownResponse.builder()
                        .category(row.getCategory())
                        .type(row.getType())
                        .totalAmount(scale2(row.getTotalAmount()))
                        .build())
                .toList();

        return FinancialSummaryResponse.builder()
                .tenantId(tenantId)
                .fromDate(range.fromDate())
                .toDate(range.toDate())
                .paidOnly(paidOnly)
                .totalOrders(totalOrders)
                .totalUnitsSold(totalUnitsSold)
                .grossSalesWithTax(grossSalesWithTax)
                .netSales(netSales)
                .taxCollected(taxCollected)
                .deliveryFeesCollected(deliveryFeesCollected)
                .costOfGoodsSold(costOfGoodsSold)
                .grossProfit(grossProfit)
                .operatingExpenses(operatingExpenses)
                .operatingProfit(operatingProfit)
                .nonOperatingExpenses(nonOperatingExpenses)
                .incomeTaxExpenses(incomeTaxExpenses)
                .netProfit(netProfit)
                .grossMarginPercentage(grossMarginPercentage)
                .operatingMarginPercentage(operatingMarginPercentage)
                .netMarginPercentage(netMarginPercentage)
                .markupPercentage(markupPercentage)
                .expensesByCategory(categoryBreakdown)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductMarginResponse> getProductMargins(
            UUID tenantId,
            LocalDate fromDate,
            LocalDate toDate,
            boolean paidOnly
    ) {
        DateRange range = resolveDateRange(fromDate, toDate);

        return financialAnalyticsRepository.aggregateProductMargins(
                        tenantId,
                        OrderStatus.CANCELADO,
                        PaymentStatus.APPROVED,
                        range.fromDateTime(),
                        range.toDateTimeExclusive(),
                        paidOnly
                )
                .stream()
                .map(row -> {
                    BigDecimal netSales = scale2(row.getNetSales());
                    BigDecimal taxCollected = scale2(row.getTaxCollected());
                    BigDecimal totalCost = scale2(row.getTotalCost());
                    BigDecimal grossProfit = netSales.subtract(totalCost).setScale(2, RoundingMode.HALF_UP);

                    return ProductMarginResponse.builder()
                            .productId(row.getProductId())
                            .productName(row.getProductName())
                            .unitsSold(row.getUnitsSold() != null ? row.getUnitsSold() : 0L)
                            .netSales(netSales)
                            .taxCollected(taxCollected)
                            .totalCost(totalCost)
                            .grossProfit(grossProfit)
                            .grossMarginPercentage(calculatePercentage(grossProfit, netSales))
                            .markupPercentage(calculatePercentage(grossProfit, totalCost))
                            .build();
                })
                .toList();
    }

    private DateRange resolveDateRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate effectiveTo = toDate != null ? toDate : LocalDate.now();
        LocalDate effectiveFrom = fromDate != null ? fromDate : effectiveTo.withDayOfMonth(1);
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BadRequestException("La fecha de inicio (fromDate) no puede ser posterior a la fecha fin (toDate).");
        }
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime fromDateTime = effectiveFrom.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime toDateTimeExclusive = effectiveTo.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        return new DateRange(effectiveFrom, effectiveTo, fromDateTime, toDateTimeExclusive);
    }

    private BigDecimal scale2(BigDecimal value) {
        return value != null ? value.setScale(2, RoundingMode.HALF_UP) : ZERO;
    }

    private BigDecimal calculatePercentage(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0 || numerator == null) {
            return ZERO;
        }
        return numerator
                .multiply(ONE_HUNDRED)
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private record DateRange(
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime fromDateTime,
            OffsetDateTime toDateTimeExclusive
    ) {}
}
