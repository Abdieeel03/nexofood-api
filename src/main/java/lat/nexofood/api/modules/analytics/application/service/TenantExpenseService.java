package lat.nexofood.api.modules.analytics.application.service;

import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.analytics.domain.ExpenseCategory;
import lat.nexofood.api.modules.analytics.domain.ExpenseType;
import lat.nexofood.api.modules.analytics.web.dto.request.TenantExpenseRequest;
import lat.nexofood.api.modules.analytics.web.dto.response.TenantExpenseResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface TenantExpenseService {

    PageResponse<TenantExpenseResponse> findAll(
            UUID tenantId,
            LocalDate fromDate,
            LocalDate toDate,
            ExpenseType type,
            ExpenseCategory category,
            Pageable pageable
    );

    TenantExpenseResponse findById(UUID tenantId, UUID expenseId);

    TenantExpenseResponse create(UUID tenantId, TenantExpenseRequest request);

    TenantExpenseResponse update(UUID tenantId, UUID expenseId, TenantExpenseRequest request);

    void delete(UUID tenantId, UUID expenseId);
}
