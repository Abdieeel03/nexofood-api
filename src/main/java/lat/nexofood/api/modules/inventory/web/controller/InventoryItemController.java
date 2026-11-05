package lat.nexofood.api.modules.inventory.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.inventory.application.service.InventoryItemService;
import lat.nexofood.api.modules.inventory.domain.InventoryUnit;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryItemRequest;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryItemStatusRequest;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/inventory/items")
@RequiredArgsConstructor
@Tag(name = "Inventory - Items", description = "Gestión de insumos y mercaderías del almacén del restaurante")
public class InventoryItemController {

    private final InventoryItemService inventoryItemService;

    @GetMapping
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Listar insumos de inventario del restaurante paginados (acceso para todo el staff)")
    public ResponseEntity<ApiResponse<PageResponse<InventoryItemResponse>>> getItems(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) InventoryUnit unit,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<InventoryItemResponse> response = inventoryItemService.findByTenantPaginated(
                tenantId,
                activeOnly,
                category,
                unit,
                pageable
        );
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<InventoryItemResponse>>builder()
                        .success(true)
                        .message("Insumos de inventario obtenidos exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/{itemId}")
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Obtener detalle de un insumo por ID (acceso para todo el staff)")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> getItemById(
            @PathVariable UUID tenantId,
            @PathVariable UUID itemId
    ) {
        InventoryItemResponse response = inventoryItemService.findByIdAndTenant(tenantId, itemId);
        return ResponseEntity.ok(
                ApiResponse.<InventoryItemResponse>builder()
                        .success(true)
                        .message("Insumo de inventario obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PostMapping
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Registrar un nuevo insumo en el inventario con SKU único por restaurante (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> createItem(
            @PathVariable UUID tenantId,
            @Valid @RequestBody InventoryItemRequest request
    ) {
        InventoryItemResponse created = inventoryItemService.create(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<InventoryItemResponse>builder()
                        .success(true)
                        .message("Insumo de inventario creado exitosamente")
                        .data(created)
                        .build()
        );
    }

    @PutMapping("/{itemId}")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Actualizar un insumo de inventario (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> updateItem(
            @PathVariable UUID tenantId,
            @PathVariable UUID itemId,
            @Valid @RequestBody InventoryItemRequest request
    ) {
        InventoryItemResponse updated = inventoryItemService.update(tenantId, itemId, request);
        return ResponseEntity.ok(
                ApiResponse.<InventoryItemResponse>builder()
                        .success(true)
                        .message("Insumo de inventario actualizado exitosamente")
                        .data(updated)
                        .build()
        );
    }

    @PatchMapping("/{itemId}/status")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Activar o desactivar un insumo de inventario (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> toggleItemStatus(
            @PathVariable UUID tenantId,
            @PathVariable UUID itemId,
            @Valid @RequestBody InventoryItemStatusRequest request
    ) {
        InventoryItemResponse updated = inventoryItemService.toggleStatus(tenantId, itemId, request.isActive());
        return ResponseEntity.ok(
                ApiResponse.<InventoryItemResponse>builder()
                        .success(true)
                        .message(Boolean.TRUE.equals(request.isActive())
                                ? "Insumo activado exitosamente"
                                : "Insumo desactivado exitosamente")
                        .data(updated)
                        .build()
        );
    }

    @DeleteMapping("/{itemId}")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Eliminar un insumo si no tiene existencias ni recetas asociadas (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<Void>> deleteItem(
            @PathVariable UUID tenantId,
            @PathVariable UUID itemId
    ) {
        inventoryItemService.delete(tenantId, itemId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Insumo de inventario eliminado exitosamente")
                        .build()
        );
    }
}
