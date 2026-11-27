package lat.nexofood.api.modules.analytics.web.dto.response;

import lat.nexofood.api.modules.analytics.domain.ExpenseCategory;
import lat.nexofood.api.modules.analytics.domain.ExpenseType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record TenantExpenseResponse(
        UUID id,
        UUID tenantId,
        UUID createdBy,
        ExpenseType type,
        ExpenseCategory category,
        String description,
        BigDecimal amount,
        LocalDate expenseDate,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
