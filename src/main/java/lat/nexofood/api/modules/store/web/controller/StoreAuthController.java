package lat.nexofood.api.modules.store.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.store.application.service.auth.StoreAuthService;
import lat.nexofood.api.modules.store.web.dto.request.StoreLoginRequest;
import lat.nexofood.api.modules.store.web.dto.request.StoreRegisterRequest;
import lat.nexofood.api.modules.store.web.dto.response.StoreAuthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/store/{tenantSlug}/auth")
@RequiredArgsConstructor
@Tag(name = "Store Auth", description = "Autenticación contextual por tienda")
public class StoreAuthController {

    private final StoreAuthService storeAuthService;

    @PostMapping("/register")
    @Operation(summary = "Registro de cliente en la tienda",
               description = "Crea una cuenta global y la asocia al tenant. Si el email ya existe, solicita login.")
    public ResponseEntity<ApiResponse<StoreAuthResponse>> register(
            @PathVariable String tenantSlug,
            @Valid @RequestBody StoreRegisterRequest request) {
        StoreAuthResponse response = storeAuthService.register(tenantSlug, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<StoreAuthResponse>builder()
                        .success(true)
                        .message("Registro exitoso en la tienda")
                        .data(response)
                        .build()
        );
    }

    @PostMapping("/login")
    @Operation(summary = "Login de cliente en la tienda",
               description = "Autentica con credenciales globales y retorna el perfil del cliente en esta tienda específica.")
    public ResponseEntity<ApiResponse<StoreAuthResponse>> login(
            @PathVariable String tenantSlug,
            @Valid @RequestBody StoreLoginRequest request) {
        StoreAuthResponse response = storeAuthService.login(tenantSlug, request);
        return ResponseEntity.ok(
                ApiResponse.<StoreAuthResponse>builder()
                        .success(true)
                        .message("Inicio de sesión exitoso en la tienda")
                        .data(response)
                        .build()
        );
    }
}
