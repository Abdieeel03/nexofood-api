package lat.nexofood.api.modules.analytics.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.analytics.application.service.FinancialDashboardService;
import lat.nexofood.api.modules.analytics.web.dto.response.FinancialSummaryResponse;
import lat.nexofood.api.modules.analytics.web.dto.response.ProductMarginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/dashboard")
@RequiredArgsConstructor
@Tag(name = "Analytics - Dashboard", description = "Métricas financieras de rentabilidad, márgenes y markup del restaurante")
public class DashboardController {

    private final FinancialDashboardService financialDashboardService;

    @GetMapping("/financial-summary")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Obtener resumen financiero (ganancia bruta, operativa, neta, márgenes y markup) por rango de fechas (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<FinancialSummaryResponse>> getFinancialSummary(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "true") boolean paidOnly
    ) {
        FinancialSummaryResponse summary = financialDashboardService.getFinancialSummary(
                tenantId, fromDate, toDate, paidOnly
        );
        return ResponseEntity.ok(
                ApiResponse.<FinancialSummaryResponse>builder()
                        .success(true)
                        .message("Resumen financiero obtenido exitosamente")
                        .data(summary)
                        .build()
        );
    }

    @GetMapping("/product-margins")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Obtener rentabilidad, margen bruto y markup desglosado por producto (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<List<ProductMarginResponse>>> getProductMargins(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "true") boolean paidOnly
    ) {
        List<ProductMarginResponse> margins = financialDashboardService.getProductMargins(
                tenantId, fromDate, toDate, paidOnly
        );
        return ResponseEntity.ok(
                ApiResponse.<List<ProductMarginResponse>>builder()
                        .success(true)
                        .message("Rentabilidad por producto obtenida exitosamente")
                        .data(margins)
                        .build()
        );
    }
}
