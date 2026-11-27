package lat.nexofood.api.modules.analytics.application.service;

import lat.nexofood.api.modules.analytics.web.dto.response.FinancialSummaryResponse;
import lat.nexofood.api.modules.analytics.web.dto.response.ProductMarginResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FinancialDashboardService {

    FinancialSummaryResponse getFinancialSummary(
            UUID tenantId,
            LocalDate fromDate,
            LocalDate toDate,
            boolean paidOnly
    );

    List<ProductMarginResponse> getProductMargins(
            UUID tenantId,
            LocalDate fromDate,
            LocalDate toDate,
            boolean paidOnly
    );
}
