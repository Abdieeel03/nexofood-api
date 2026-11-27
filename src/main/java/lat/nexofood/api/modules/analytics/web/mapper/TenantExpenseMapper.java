package lat.nexofood.api.modules.analytics.web.mapper;

import lat.nexofood.api.modules.analytics.domain.TenantExpense;
import lat.nexofood.api.modules.analytics.web.dto.request.TenantExpenseRequest;
import lat.nexofood.api.modules.analytics.web.dto.response.TenantExpenseResponse;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.store.domain.Tenant;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;

@Component
public class TenantExpenseMapper {

    public TenantExpenseResponse toResponse(TenantExpense expense) {
        if (expense == null) {
            return null;
        }
        return TenantExpenseResponse.builder()
                .id(expense.getId())
                .tenantId(expense.getTenant() != null ? expense.getTenant().getId() : null)
                .createdBy(expense.getCreatedBy() != null ? expense.getCreatedBy().getId() : null)
                .type(expense.getType())
                .category(expense.getCategory())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .expenseDate(expense.getExpenseDate())
                .notes(expense.getNotes())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
    }

    public TenantExpense toEntity(TenantExpenseRequest request, Tenant tenant, User createdBy) {
        if (request == null) {
            return null;
        }
        return TenantExpense.builder()
                .tenant(tenant)
                .createdBy(createdBy)
                .type(request.type())
                .category(request.category())
                .description(request.description() != null ? request.description().trim() : null)
                .amount(request.amount() != null ? request.amount().setScale(2, RoundingMode.HALF_UP) : null)
                .expenseDate(request.expenseDate())
                .notes(request.notes())
                .build();
    }
}
