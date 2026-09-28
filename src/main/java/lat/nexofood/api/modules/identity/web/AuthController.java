package lat.nexofood.api.modules.identity.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.identity.application.service.AuthService;
import lat.nexofood.api.modules.identity.web.dto.request.LoginRequest;
import lat.nexofood.api.modules.identity.web.dto.request.RefreshTokenRequest;
import lat.nexofood.api.modules.identity.web.dto.request.UserRegisterRequest;
import lat.nexofood.api.modules.identity.web.dto.response.AuthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import lat.nexofood.api.modules.identity.web.dto.response.UserProfileResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Endpoints de autenticación, registro y gestión de tokens")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Registrar nuevo usuario", description = "Crea una cuenta de usuario y genera los tokens de sesión iniciales")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody UserRegisterRequest request) {
        AuthResponse response = authService.register(request);
        ApiResponse<AuthResponse> apiResponse = ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Usuario registrado exitosamente")
                .data(response)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Autentica credenciales y emite tokens de acceso y refresco")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        ApiResponse<AuthResponse> apiResponse = ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Inicio de sesión exitoso")
                .data(response)
                .build();
        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar tokens", description = "Genera un nuevo access token y rota el refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        ApiResponse<AuthResponse> apiResponse = ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Token renovado exitosamente")
                .data(response)
                .build();
        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/me")
    @Operation(summary = "Perfil del usuario autenticado", description = "Retorna datos del usuario, sus membresías de staff y direcciones guardadas")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me(
            @AuthenticationPrincipal UserDetails userDetails) {
        UserProfileResponse profile = authService.getProfile(userDetails.getUsername());
        return ResponseEntity.ok(
                ApiResponse.<UserProfileResponse>builder()
                        .success(true)
                        .message("Perfil obtenido exitosamente")
                        .data(profile)
                        .build()
        );
    }
}
