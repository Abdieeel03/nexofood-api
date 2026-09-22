package lat.nexofood.api.modules.identity.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.GlobalExceptionHandler;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.UnauthorizedException;
import lat.nexofood.api.modules.identity.application.service.AuthService;
import lat.nexofood.api.modules.identity.domain.UserSystemRole;
import lat.nexofood.api.modules.identity.web.dto.request.LoginRequest;
import lat.nexofood.api.modules.identity.web.dto.request.RefreshTokenRequest;
import lat.nexofood.api.modules.identity.web.dto.request.UserRegisterRequest;
import lat.nexofood.api.modules.identity.web.dto.response.AuthResponse;
import lat.nexofood.api.modules.identity.web.dto.response.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private AuthResponse sampleAuthResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UserResponse userResponse = UserResponse.builder()
                .id(UUID.randomUUID())
                .email("test@nexofood.lat")
                .fullName("Juan Pérez")
                .phone("+51999888777")
                .systemRole(UserSystemRole.USER)
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        sampleAuthResponse = AuthResponse.builder()
                .accessToken("mock-access-token")
                .refreshToken("mock-refresh-token")
                .tokenType("Bearer")
                .expiresIn(900)
                .user(userResponse)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - 201 Created cuando los datos son válidos")
    void registerShouldReturn201() throws Exception {
        UserRegisterRequest request = new UserRegisterRequest(
                "test@nexofood.lat",
                "password123",
                "Juan Pérez",
                "+51999888777"
        );

        when(authService.register(any(UserRegisterRequest.class))).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Usuario registrado exitosamente"))
                .andExpect(jsonPath("$.data.accessToken").value("mock-access-token"))
                .andExpect(jsonPath("$.data.user.email").value("test@nexofood.lat"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - 400 Bad Request cuando el email es inválido")
    void registerShouldReturn400WhenInvalidEmail() throws Exception {
        UserRegisterRequest request = new UserRegisterRequest(
                "invalid-email",
                "password123",
                "Juan Pérez",
                null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("email")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - 409 Conflict cuando el email ya existe")
    void registerShouldReturn409WhenDuplicateEmail() throws Exception {
        UserRegisterRequest request = new UserRegisterRequest(
                "test@nexofood.lat",
                "password123",
                "Juan Pérez",
                null
        );

        when(authService.register(any(UserRegisterRequest.class)))
                .thenThrow(new ResourceConflictException(ErrorMessages.EMAIL_ALREADY_EXISTS));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(ErrorMessages.EMAIL_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - 200 OK con credenciales correctas")
    void loginShouldReturn200() throws Exception {
        LoginRequest request = new LoginRequest("test@nexofood.lat", "password123");

        when(authService.login(any(LoginRequest.class))).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Inicio de sesión exitoso"))
                .andExpect(jsonPath("$.data.accessToken").value("mock-access-token"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - 401 Unauthorized cuando las credenciales son incorrectas")
    void loginShouldReturn401WhenInvalidCredentials() throws Exception {
        LoginRequest request = new LoginRequest("test@nexofood.lat", "wrong-password");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException(ErrorMessages.INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(ErrorMessages.INVALID_CREDENTIALS));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - 200 OK con token válido")
    void refreshShouldReturn200() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("mock-refresh-token");

        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Token renovado exitosamente"))
                .andExpect(jsonPath("$.data.refreshToken").value("mock-refresh-token"));
    }
}
