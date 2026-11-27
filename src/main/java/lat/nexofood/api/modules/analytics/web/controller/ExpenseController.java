package lat.nexofood.api.modules.analytics.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.analytics.application.service.TenantExpenseService;
import lat.nexofood.api.modules.analytics.domain.ExpenseCategory;
import lat.nexofood.api.modules.analytics.domain.ExpenseType;
import lat.nexofood.api.modules.analytics.web.dto.request.TenantExpenseRequest;
import lat.nexofood.api.modules.analytics.web.dto.response.TenantExpenseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/expenses")
@RequiredArgsConstructor
@Tag(name = "Analytics - Expenses", description = "Gestión de gastos operativos, no operativos e impuestos del restaurante")
public class ExpenseController {

    private final TenantExpenseService expenseService;

    @GetMapping
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Listar gastos del restaurante con paginación y filtros (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<PageResponse<TenantExpenseResponse>>> getExpenses(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) ExpenseType type,
            @RequestParam(required = false) ExpenseCategory category,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<TenantExpenseResponse> page = expenseService.findAll(
                tenantId, fromDate, toDate, type, category, pageable
        );
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<TenantExpenseResponse>>builder()
                        .success(true)
                        .message("Gastos del restaurante obtenidos exitosamente")
                        .data(page)
                        .build()
        );
    }

    @GetMapping("/{expenseId}")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Obtener detalle de un gasto por ID (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<TenantExpenseResponse>> getExpenseById(
            @PathVariable UUID tenantId,
            @PathVariable UUID expenseId
    ) {
        TenantExpenseResponse response = expenseService.findById(tenantId, expenseId);
        return ResponseEntity.ok(
                ApiResponse.<TenantExpenseResponse>builder()
                        .success(true)
                        .message("Gasto obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PostMapping
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Registrar un nuevo gasto del restaurante (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<TenantExpenseResponse>> createExpense(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TenantExpenseRequest request
    ) {
        TenantExpenseResponse created = expenseService.create(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<TenantExpenseResponse>builder()
                        .success(true)
                        .message("Gasto registrado exitosamente")
                        .data(created)
                        .build()
        );
    }

    @PutMapping("/{expenseId}")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Actualizar un gasto existente del restaurante (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<TenantExpenseResponse>> updateExpense(
            @PathVariable UUID tenantId,
            @PathVariable UUID expenseId,
            @Valid @RequestBody TenantExpenseRequest request
    ) {
        TenantExpenseResponse updated = expenseService.update(tenantId, expenseId, request);
        return ResponseEntity.ok(
                ApiResponse.<TenantExpenseResponse>builder()
                        .success(true)
                        .message("Gasto actualizado exitosamente")
                        .data(updated)
                        .build()
        );
    }

    @DeleteMapping("/{expenseId}")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Eliminar un gasto del restaurante (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(
            @PathVariable UUID tenantId,
            @PathVariable UUID expenseId
    ) {
        expenseService.delete(tenantId, expenseId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Gasto eliminado exitosamente")
                        .build()
        );
    }
}
