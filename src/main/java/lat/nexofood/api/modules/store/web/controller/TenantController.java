package lat.nexofood.api.modules.store.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.store.application.service.tenant.TenantService;
import lat.nexofood.api.modules.store.web.dto.request.TenantCreateRequest;
import lat.nexofood.api.modules.store.web.dto.request.TenantUpdateRequest;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenants", description = "Gestión de restaurantes/tiendas")
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    @Operation(summary = "Crear nuevo restaurante")
    public ResponseEntity<ApiResponse<TenantResponse>> createTenant(
            @Valid @RequestBody TenantCreateRequest request) {
        TenantResponse response = tenantService.createTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<TenantResponse>builder()
                        .success(true)
                        .message("Restaurante creado exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener restaurante por ID")
    public ResponseEntity<ApiResponse<TenantResponse>> getTenantById(@PathVariable UUID id) {
        TenantResponse response = tenantService.getTenantById(id);
        return ResponseEntity.ok(
                ApiResponse.<TenantResponse>builder()
                        .success(true)
                        .message("Restaurante obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Obtener restaurante por slug (público, sin token)")
    public ResponseEntity<ApiResponse<TenantResponse>> getTenantBySlug(@PathVariable String slug) {
        TenantResponse response = tenantService.getTenantBySlug(slug);
        return ResponseEntity.ok(
                ApiResponse.<TenantResponse>builder()
                        .success(true)
                        .message("Restaurante obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar datos del restaurante")
    public ResponseEntity<ApiResponse<TenantResponse>> updateTenant(
            @PathVariable UUID id,
            @Valid @RequestBody TenantUpdateRequest request) {
        TenantResponse response = tenantService.updateTenant(id, request);
        return ResponseEntity.ok(
                ApiResponse.<TenantResponse>builder()
                        .success(true)
                        .message("Restaurante actualizado exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activar o desactivar el restaurante")
    public ResponseEntity<ApiResponse<Void>> changeStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body) {
        Boolean isActive = body.get("isActive");
        if (isActive == null) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.<Void>builder()
                            .success(false)
                            .message("El campo 'isActive' es obligatorio")
                            .build()
            );
        }
        tenantService.changeStatus(id, isActive);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Estado del restaurante actualizado")
                        .build()
        );
    }

    @GetMapping("/me")
    @Operation(summary = "Obtener el restaurante del usuario autenticado")
    public ResponseEntity<ApiResponse<TenantResponse>> getMyTenant(
            @AuthenticationPrincipal UserDetails userDetails) {
        TenantResponse response = tenantService.getMyTenant(userDetails.getUsername());
        return ResponseEntity.ok(
                ApiResponse.<TenantResponse>builder()
                        .success(true)
                        .message("Restaurante del usuario obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }
}
