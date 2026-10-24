package lat.nexofood.api.modules.identity.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.identity.application.service.profile.UserService;
import lat.nexofood.api.modules.identity.web.dto.request.ChangePasswordRequest;
import lat.nexofood.api.modules.identity.web.dto.request.UserUpdateRequest;
import lat.nexofood.api.modules.identity.web.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "Perfil de Usuario", description = "Gestión del perfil y cuenta del usuario autenticado")
public class UserController {

    private final UserService userService;

    @PutMapping
    @Operation(summary = "Actualizar datos de mi perfil")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(
            @Valid @RequestBody UserUpdateRequest request) {
        UserResponse response = userService.updateMyProfile(request);
        return ResponseEntity.ok(
                ApiResponse.<UserResponse>builder()
                        .success(true)
                        .message("Perfil actualizado exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PatchMapping("/password")
    @Operation(summary = "Cambiar mi contraseña", description = "Actualiza la contraseña actual y revoca todas las sesiones activas")
    public ResponseEntity<ApiResponse<Void>> changeMyPassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changeMyPassword(request);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Contraseña actualizada exitosamente")
                        .build()
        );
    }

    @DeleteMapping
    @Operation(summary = "Eliminar mi cuenta (soft delete)", description = "Desactiva la cuenta lógicamente y revoca los tokens")
    public ResponseEntity<ApiResponse<Void>> deleteMyAccount() {
        userService.deleteMyAccount();
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Cuenta eliminada exitosamente")
                        .build()
        );
    }
}
