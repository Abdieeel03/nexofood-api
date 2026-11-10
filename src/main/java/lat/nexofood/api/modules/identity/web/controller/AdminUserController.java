package lat.nexofood.api.modules.identity.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.identity.application.service.profile.UserService;
import lat.nexofood.api.modules.identity.web.dto.request.UserStatusUpdateRequest;
import lat.nexofood.api.modules.identity.web.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("@tenantAccess.isSuperAdmin()")
@Tag(name = "Administración de Usuarios (SUPERADMIN)", description = "Listado y activación/desactivación de cuentas globales")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Listar usuarios de la plataforma (paginado)")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAllUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<UserResponse> page = userService.getAllUsers(pageable);
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<UserResponse>>builder()
                        .success(true)
                        .message("Usuarios obtenidos exitosamente")
                        .data(PageResponse.from(page))
                        .build()
        );
    }

    @PatchMapping("/{userId}/status")
    @Operation(summary = "Activar o desactivar cuenta de usuario")
    public ResponseEntity<ApiResponse<UserResponse>> changeUserStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UserStatusUpdateRequest request) {
        UserResponse response = userService.changeUserStatus(userId, request.isActive());
        return ResponseEntity.ok(
                ApiResponse.<UserResponse>builder()
                        .success(true)
                        .message(Boolean.TRUE.equals(request.isActive()) ? "Usuario activado" : "Usuario desactivado")
                        .data(response)
                        .build()
        );
    }
}
