package lat.nexofood.api.modules.store.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.store.application.service.customer.TenantCustomerService;
import lat.nexofood.api.modules.store.web.dto.response.TenantCustomerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/tenants/{tenantId}/customers")
@RequiredArgsConstructor
@Tag(name = "Tenant Customers", description = "Gestión de clientes del restaurante (panel admin)")
public class TenantCustomerController {

    private final TenantCustomerService tenantCustomerService;

    @GetMapping
    @Operation(summary = "Listar clientes de la tienda (con paginación)")
    public ResponseEntity<ApiResponse<Page<TenantCustomerResponse>>> getCustomers(
            @PathVariable UUID tenantId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<TenantCustomerResponse> page = tenantCustomerService.getCustomers(tenantId, pageable);
        return ResponseEntity.ok(
                ApiResponse.<Page<TenantCustomerResponse>>builder()
                        .success(true)
                        .message("Clientes obtenidos exitosamente")
                        .data(page)
                        .build()
        );
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Ver perfil del cliente en esta tienda")
    public ResponseEntity<ApiResponse<TenantCustomerResponse>> getCustomerById(
            @PathVariable UUID tenantId,
            @PathVariable UUID customerId) {
        TenantCustomerResponse response = tenantCustomerService.getCustomerById(tenantId, customerId);
        return ResponseEntity.ok(
                ApiResponse.<TenantCustomerResponse>builder()
                        .success(true)
                        .message("Cliente obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PatchMapping("/{customerId}/block")
    @Operation(summary = "Bloquear o desbloquear cliente en esta tienda")
    public ResponseEntity<ApiResponse<TenantCustomerResponse>> blockCustomer(
            @PathVariable UUID tenantId,
            @PathVariable UUID customerId,
            @RequestBody Map<String, Boolean> body) {
        Boolean isBlocked = body.get("isBlocked");
        if (isBlocked == null) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.<TenantCustomerResponse>builder()
                            .success(false)
                            .message("El campo 'isBlocked' es obligatorio")
                            .build()
            );
        }
        TenantCustomerResponse response = tenantCustomerService.blockCustomer(tenantId, customerId, isBlocked);
        return ResponseEntity.ok(
                ApiResponse.<TenantCustomerResponse>builder()
                        .success(true)
                        .message(isBlocked ? "Cliente bloqueado" : "Cliente desbloqueado")
                        .data(response)
                        .build()
        );
    }

    @PatchMapping("/{customerId}/notes")
    @Operation(summary = "Guardar notas privadas sobre el cliente")
    public ResponseEntity<ApiResponse<TenantCustomerResponse>> updateNotes(
            @PathVariable UUID tenantId,
            @PathVariable UUID customerId,
            @RequestBody Map<String, String> body) {
        String notes = body.get("notes");
        TenantCustomerResponse response = tenantCustomerService.updateNotes(tenantId, customerId, notes);
        return ResponseEntity.ok(
                ApiResponse.<TenantCustomerResponse>builder()
                        .success(true)
                        .message("Notas actualizadas exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PatchMapping("/{customerId}/points")
    @Operation(summary = "Ajustar puntos de fidelidad del cliente")
    public ResponseEntity<ApiResponse<TenantCustomerResponse>> adjustPoints(
            @PathVariable UUID tenantId,
            @PathVariable UUID customerId,
            @RequestBody Map<String, Integer> body) {
        Integer adjustment = body.get("adjustment");
        if (adjustment == null) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.<TenantCustomerResponse>builder()
                            .success(false)
                            .message("El campo 'adjustment' es obligatorio")
                            .build()
            );
        }
        TenantCustomerResponse response = tenantCustomerService.adjustPoints(tenantId, customerId, adjustment);
        return ResponseEntity.ok(
                ApiResponse.<TenantCustomerResponse>builder()
                        .success(true)
                        .message("Puntos ajustados exitosamente")
                        .data(response)
                        .build()
        );
    }
}
