package lat.nexofood.api.modules.store.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.store.application.service.tenant.TenantService;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.web.dto.request.TenantMemberRequest;
import lat.nexofood.api.modules.store.web.dto.response.TenantMemberResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/members")
@RequiredArgsConstructor
@Tag(name = "Tenant Members", description = "Gestión del equipo/staff del restaurante")
public class TenantMemberController {

    private final TenantService tenantService;

    @GetMapping
    @Operation(summary = "Listar equipo del restaurante")
    public ResponseEntity<ApiResponse<List<TenantMemberResponse>>> getMembers(
            @PathVariable UUID tenantId) {
        List<TenantMemberResponse> members = tenantService.getMembers(tenantId);
        return ResponseEntity.ok(
                ApiResponse.<List<TenantMemberResponse>>builder()
                        .success(true)
                        .message("Miembros obtenidos exitosamente")
                        .data(members)
                        .build()
        );
    }

    @PostMapping
    @Operation(summary = "Agregar nuevo empleado al restaurante")
    public ResponseEntity<ApiResponse<TenantMemberResponse>> addMember(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TenantMemberRequest request) {
        TenantMemberResponse response = tenantService.addMember(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<TenantMemberResponse>builder()
                        .success(true)
                        .message("Miembro agregado exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PatchMapping("/{memberId}/role")
    @Operation(summary = "Cambiar rol de un empleado")
    public ResponseEntity<ApiResponse<TenantMemberResponse>> updateMemberRole(
            @PathVariable UUID tenantId,
            @PathVariable UUID memberId,
            @RequestBody Map<String, String> body) {
        TenantStaffRole newRole = TenantStaffRole.valueOf(body.get("role"));
        TenantMemberResponse response = tenantService.updateMemberRole(tenantId, memberId, newRole);
        return ResponseEntity.ok(
                ApiResponse.<TenantMemberResponse>builder()
                        .success(true)
                        .message("Rol actualizado exitosamente")
                        .data(response)
                        .build()
        );
    }

    @DeleteMapping("/{memberId}")
    @Operation(summary = "Desvincular empleado del restaurante")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable UUID tenantId,
            @PathVariable UUID memberId) {
        tenantService.removeMember(tenantId, memberId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Miembro desvinculado exitosamente")
                        .build()
        );
    }
}
