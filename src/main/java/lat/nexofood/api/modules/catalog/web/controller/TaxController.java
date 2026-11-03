package lat.nexofood.api.modules.catalog.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.catalog.application.service.TaxService;
import lat.nexofood.api.modules.catalog.web.dto.request.TaxRequest;
import lat.nexofood.api.modules.catalog.web.dto.request.TaxStatusRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.TaxResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/taxes")
@RequiredArgsConstructor
@Tag(name = "Catalog - Taxes", description = "Gestión de impuestos y configuración tributaria del restaurante")
public class TaxController {

    private final TaxService taxService;

    @GetMapping
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Listar impuestos del restaurante (acceso para cualquier miembro del staff)")
    public ResponseEntity<ApiResponse<List<TaxResponse>>> getTaxes(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
        List<TaxResponse> taxes = activeOnly
                ? taxService.findActiveByTenant(tenantId)
                : taxService.findAllByTenant(tenantId);

        return ResponseEntity.ok(
                ApiResponse.<List<TaxResponse>>builder()
                        .success(true)
                        .message("Impuestos obtenidos exitosamente")
                        .data(taxes)
                        .build()
        );
    }

    @GetMapping("/{taxId}")
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Obtener impuesto por ID (acceso para cualquier miembro del staff)")
    public ResponseEntity<ApiResponse<TaxResponse>> getTaxById(
            @PathVariable UUID tenantId,
            @PathVariable UUID taxId
    ) {
        TaxResponse tax = taxService.findByIdAndTenant(taxId, tenantId);
        return ResponseEntity.ok(
                ApiResponse.<TaxResponse>builder()
                        .success(true)
                        .message("Impuesto obtenido exitosamente")
                        .data(tax)
                        .build()
        );
    }

    @PostMapping
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Crear un nuevo impuesto para el restaurante (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<TaxResponse>> createTax(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TaxRequest request
    ) {
        TaxResponse created = taxService.create(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<TaxResponse>builder()
                        .success(true)
                        .message("Impuesto creado exitosamente")
                        .data(created)
                        .build()
        );
    }

    @PutMapping("/{taxId}")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Actualizar un impuesto del restaurante (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<TaxResponse>> updateTax(
            @PathVariable UUID tenantId,
            @PathVariable UUID taxId,
            @Valid @RequestBody TaxRequest request
    ) {
        TaxResponse updated = taxService.update(tenantId, taxId, request);
        return ResponseEntity.ok(
                ApiResponse.<TaxResponse>builder()
                        .success(true)
                        .message("Impuesto actualizado exitosamente")
                        .data(updated)
                        .build()
        );
    }

    @PatchMapping("/{taxId}/status")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Activar o desactivar un impuesto (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<TaxResponse>> toggleTaxStatus(
            @PathVariable UUID tenantId,
            @PathVariable UUID taxId,
            @Valid @RequestBody TaxStatusRequest request
    ) {
        TaxResponse updated = taxService.toggleStatus(tenantId, taxId, request.isActive());
        return ResponseEntity.ok(
                ApiResponse.<TaxResponse>builder()
                        .success(true)
                        .message(Boolean.TRUE.equals(request.isActive())
                                ? "Impuesto activado exitosamente"
                                : "Impuesto desactivado exitosamente")
                        .data(updated)
                        .build()
        );
    }

    @DeleteMapping("/{taxId}")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Eliminar un impuesto si no está en uso (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<Void>> deleteTax(
            @PathVariable UUID tenantId,
            @PathVariable UUID taxId
    ) {
        taxService.delete(tenantId, taxId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Impuesto eliminado exitosamente")
                        .build()
        );
    }
}
