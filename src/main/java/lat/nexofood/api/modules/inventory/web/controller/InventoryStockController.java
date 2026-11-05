package lat.nexofood.api.modules.inventory.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.inventory.application.service.InventoryMovementService;
import lat.nexofood.api.modules.inventory.application.service.InventoryStockService;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryStockRequest;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryStockUpdateRequest;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryMovementResponse;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryStockResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/tenants/{tenantId}/inventory/stocks")
@RequiredArgsConstructor
@Tag(name = "Inventory - Stocks", description = "Gestión de existencias de almacén, alertas de stock crítico y Kardex por insumo")
public class InventoryStockController {

    private final InventoryStockService inventoryStockService;
    private final InventoryMovementService inventoryMovementService;

    @GetMapping
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Listar existencias de almacén del restaurante paginadas (acceso para todo el staff)")
    public ResponseEntity<ApiResponse<PageResponse<InventoryStockResponse>>> getStocks(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) Boolean activeOnly,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<InventoryStockResponse> response = inventoryStockService.findByTenantPaginated(
                tenantId,
                activeOnly,
                pageable
        );
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<InventoryStockResponse>>builder()
                        .success(true)
                        .message("Existencias de almacén obtenidas exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/low-stock")
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Obtener alertas de stock bajo (quantity <= minimumStock) del restaurante")
    public ResponseEntity<ApiResponse<List<InventoryStockResponse>>> getLowStockAlerts(
            @PathVariable UUID tenantId
    ) {
        List<InventoryStockResponse> alerts = inventoryStockService.findLowStockAlerts(tenantId);
        return ResponseEntity.ok(
                ApiResponse.<List<InventoryStockResponse>>builder()
                        .success(true)
                        .message("Alertas de stock bajo obtenidas exitosamente")
                        .data(alerts)
                        .build()
        );
    }

    @GetMapping("/{stockId}")
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Obtener registro de existencias (stock) por ID")
    public ResponseEntity<ApiResponse<InventoryStockResponse>> getStockById(
            @PathVariable UUID tenantId,
            @PathVariable UUID stockId
    ) {
        InventoryStockResponse response = inventoryStockService.findByIdAndTenant(tenantId, stockId);
        return ResponseEntity.ok(
                ApiResponse.<InventoryStockResponse>builder()
                        .success(true)
                        .message("Registro de existencias obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/item/{itemId}")
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Obtener registro de existencias (stock) por ID de insumo")
    public ResponseEntity<ApiResponse<InventoryStockResponse>> getStockByItemId(
            @PathVariable UUID tenantId,
            @PathVariable UUID itemId
    ) {
        InventoryStockResponse response = inventoryStockService.findByItemAndTenant(tenantId, itemId);
        return ResponseEntity.ok(
                ApiResponse.<InventoryStockResponse>builder()
                        .success(true)
                        .message("Registro de existencias obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PostMapping
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Inicializar un registro de existencias (stock en 0) para un insumo del restaurante (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<InventoryStockResponse>> createStock(
            @PathVariable UUID tenantId,
            @Valid @RequestBody InventoryStockRequest request
    ) {
        InventoryStockResponse created = inventoryStockService.create(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<InventoryStockResponse>builder()
                        .success(true)
                        .message("Registro de existencias creado exitosamente")
                        .data(created)
                        .build()
        );
    }

    @PutMapping("/{stockId}")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Actualizar parámetros de un registro de stock (minimumStock, maximumStock, ubicación, estado) (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<InventoryStockResponse>> updateStock(
            @PathVariable UUID tenantId,
            @PathVariable UUID stockId,
            @Valid @RequestBody InventoryStockUpdateRequest request
    ) {
        InventoryStockResponse updated = inventoryStockService.update(tenantId, stockId, request);
        return ResponseEntity.ok(
                ApiResponse.<InventoryStockResponse>builder()
                        .success(true)
                        .message("Registro de existencias actualizado exitosamente")
                        .data(updated)
                        .build()
        );
    }

    @GetMapping("/{stockId}/kardex")
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Consultar el Kardex (historial paginado de movimientos) de un registro de stock")
    public ResponseEntity<ApiResponse<PageResponse<InventoryMovementResponse>>> getStockKardex(
            @PathVariable UUID tenantId,
            @PathVariable UUID stockId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<InventoryMovementResponse> kardex = inventoryMovementService.getKardexByStock(
                tenantId,
                stockId,
                pageable
        );
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<InventoryMovementResponse>>builder()
                        .success(true)
                        .message("Kardex de existencias obtenido exitosamente")
                        .data(kardex)
                        .build()
        );
    }
}
