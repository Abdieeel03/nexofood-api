package lat.nexofood.api.modules.inventory.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.inventory.application.service.InventoryMovementService;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementReason;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementType;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryMovementRequest;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryMovementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/inventory/movements")
@RequiredArgsConstructor
@Tag(name = "Inventory - Movements (Kardex)", description = "Registro transaccional y consulta de movimientos de inventario (entradas, salidas y ajustes)")
public class InventoryMovementController {

    private final InventoryMovementService inventoryMovementService;

    @GetMapping
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Listar movimientos de inventario del restaurante paginados con filtros opcionales")
    public ResponseEntity<ApiResponse<PageResponse<InventoryMovementResponse>>> getMovements(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) InventoryMovementType movementType,
            @RequestParam(required = false) InventoryMovementReason reason,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<InventoryMovementResponse> response = inventoryMovementService.findByTenantPaginated(
                tenantId,
                movementType,
                reason,
                pageable
        );
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<InventoryMovementResponse>>builder()
                        .success(true)
                        .message("Movimientos de inventario obtenidos exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PostMapping
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).COCINERO)")
    @Operation(summary = "Registrar un movimiento de inventario (ENTRY, EXIT, ADJUSTMENT) con bloqueo pesimista")
    public ResponseEntity<ApiResponse<InventoryMovementResponse>> registerMovement(
            @PathVariable UUID tenantId,
            @Valid @RequestBody InventoryMovementRequest request
    ) {
        InventoryMovementResponse created = inventoryMovementService.register(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<InventoryMovementResponse>builder()
                        .success(true)
                        .message("Movimiento de inventario registrado exitosamente")
                        .data(created)
                        .build()
        );
    }
}
