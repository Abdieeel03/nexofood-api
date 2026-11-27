package lat.nexofood.api.modules.analytics.web.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record ProductMarginResponse(
        UUID productId,
        String productName,
        long unitsSold,
        BigDecimal netSales,
        BigDecimal taxCollected,
        BigDecimal totalCost,
        BigDecimal grossProfit,
        BigDecimal grossMarginPercentage,
        BigDecimal markupPercentage
) {}
