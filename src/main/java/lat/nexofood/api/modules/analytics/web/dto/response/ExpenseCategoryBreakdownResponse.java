package lat.nexofood.api.modules.analytics.web.dto.response;

import lat.nexofood.api.modules.analytics.domain.ExpenseCategory;
import lat.nexofood.api.modules.analytics.domain.ExpenseType;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record ExpenseCategoryBreakdownResponse(
        ExpenseCategory category,
        ExpenseType type,
        BigDecimal totalAmount
) {}
