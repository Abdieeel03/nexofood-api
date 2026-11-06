package lat.nexofood.api.security.config;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.GlobalExceptionHandler;
import lat.nexofood.api.common.exception.UnauthorizedException;
import lat.nexofood.api.common.response.ErrorResponse;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.common.security.PasswordPolicyValidator;
import lat.nexofood.api.modules.identity.application.service.LoginService;
import lat.nexofood.api.modules.identity.application.service.RefreshTokenCleanupJob;
import lat.nexofood.api.modules.identity.application.service.profile.ChangePasswordService;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.domain.UserSystemRole;
import lat.nexofood.api.modules.identity.infrastructure.repository.RefreshTokenRepository;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lat.nexofood.api.modules.identity.web.dto.request.ChangePasswordRequest;
import lat.nexofood.api.modules.identity.web.dto.request.LoginRequest;
import lat.nexofood.api.modules.identity.web.mapper.UserMapper;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lat.nexofood.api.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityHardeningTest {

    private static final String SECRET = "abcdefghijklmnopqrstuvwxyz1234567890ABCDEF";
    private static final String REFRESH_SECRET = "9876543210zyxwvutsrqponmlkjihgfedcbaFEDCBA";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "refreshSecretKey", REFRESH_SECRET);
        ReflectionTestUtils.setField(jwtService, "expirationTime", 60000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpirationTime", 120000L);
    }

    @Test
    @DisplayName("PasswordPolicyValidator debe normalizar el email (trim + lowercase) y rechazar contraseñas débiles")
    void shouldNormalizeEmailAndValidatePasswordPolicy() {
        assertThat(PasswordPolicyValidator.normalizeEmail("  Admin.User@NexoFood.LAT  "))
                .isEqualTo("admin.user@nexofood.lat");

        assertThatThrownBy(() -> PasswordPolicyValidator.validatePassword("soloLetras"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ErrorMessages.WEAK_PASSWORD);

        assertThatThrownBy(() -> PasswordPolicyValidator.validatePassword("12345678"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ErrorMessages.WEAK_PASSWORD);

        assertThatThrownBy(() -> PasswordPolicyValidator.validatePassword("pass 1234"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ErrorMessages.WEAK_PASSWORD);

        // Contraseña válida con letras y números sin espacios
        PasswordPolicyValidator.validatePassword("SecurePass123");
    }

    @Test
    @DisplayName("LoginService debe bloquear temporalmente la cuenta tras 5 intentos fallidos consecutivos")
    void shouldLockAccountTemporarilyAfterMaxFailedAttempts() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("victim@nexofood.lat")
                .passwordHash("$2a$10$hash")
                .fullName("Usuario")
                .systemRole(UserSystemRole.USER)
                .isActive(true)
                .build();

        LoginService loginService = new LoginService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService,
                new UserMapper(),
                tenantMemberRepository
        );

        when(userRepository.findByEmail("victim@nexofood.lat")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("badPass123", "$2a$10$hash")).thenReturn(false);

        for (int i = 0; i < LoginService.MAX_FAILED_ATTEMPTS; i++) {
            assertThatThrownBy(() -> loginService.execute(new LoginRequest("  VICTIM@nexofood.lat ", "badPass123")))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage(ErrorMessages.INVALID_CREDENTIALS);
        }

        assertThat(user.isAccountLocked()).isTrue();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(LoginService.MAX_FAILED_ATTEMPTS);

        // El siguiente intento debe ser rechazado por cuenta bloqueada incluso con la contraseña correcta
        assertThatThrownBy(() -> loginService.execute(new LoginRequest("victim@nexofood.lat", "correctPass123")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(ErrorMessages.ACCOUNT_LOCKED);
    }

    @Test
    @DisplayName("JWT debe incluir iss/aud/type e invalidar access tokens previos cuando cambia tokenVersion al actualizar contraseña")
    void shouldValidateJwtClaimsAndInvalidateAccessTokensOnPasswordChange() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@nexofood.lat")
                .passwordHash("$2a$10$oldHash")
                .fullName("User")
                .systemRole(UserSystemRole.USER)
                .isActive(true)
                .tokenVersion(0)
                .build();

        String accessTokenV0 = jwtService.generateToken(user);
        assertThat(jwtService.extractIssuer(accessTokenV0)).isEqualTo("nexofood-api");
        assertThat(jwtService.extractAudience(accessTokenV0)).contains("nexofood-client");
        assertThat(jwtService.extractTokenType(accessTokenV0)).isEqualTo(JwtService.TOKEN_TYPE_ACCESS);
        assertThat(jwtService.isTokenValid(accessTokenV0, user)).isTrue();

        // Al cambiar la contraseña se incrementa tokenVersion
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(passwordEncoder.matches("oldPass123", "$2a$10$oldHash")).thenReturn(true);
        when(passwordEncoder.matches("newPass456", "$2a$10$oldHash")).thenReturn(false);
        when(passwordEncoder.encode("newPass456")).thenReturn("$2a$10$newHash");

        ChangePasswordService changePasswordService = new ChangePasswordService(
                userRepository,
                refreshTokenRepository,
                currentUserProvider,
                passwordEncoder
        );
        changePasswordService.execute(new ChangePasswordRequest("oldPass123", "newPass456"));

        assertThat(user.getTokenVersion()).isEqualTo(1);
        // El token emitido con tokenVersion=0 queda inmediatamente inválido
        assertThat(jwtService.isTokenValid(accessTokenV0, user)).isFalse();

        // Un nuevo token emitido tras el cambio sí es válido
        String accessTokenV1 = jwtService.generateToken(user);
        assertThat(jwtService.isTokenValid(accessTokenV1, user)).isTrue();
    }

    @Test
    @DisplayName("RefreshTokenCleanupJob debe eliminar tokens expirados o revocados")
    void shouldPurgeExpiredOrRevokedRefreshTokens() {
        when(refreshTokenRepository.deleteExpiredOrRevokedTokens(any(LocalDateTime.class))).thenReturn(4);

        RefreshTokenCleanupJob job = new RefreshTokenCleanupJob(refreshTokenRepository);
        int deleted = job.purgeExpiredAndRevokedTokens();

        assertThat(deleted).isEqualTo(4);
        verify(refreshTokenRepository).deleteExpiredOrRevokedTokens(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("GlobalExceptionHandler no debe exponer detalles internos de excepciones en respuestas 500 (H12)")
    void shouldNotExposeInternalExceptionDetailsOn500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");

        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(
                new RuntimeException("SELECT * FROM secret_table failed: syntax error near PostgreSQL"),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Ocurrió un error interno en el servidor");
        assertThat(response.getBody().getEx()).isNull();
    }
}
