package lat.nexofood.api.modules.identity.application.service;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.UnauthorizedException;
import lat.nexofood.api.modules.identity.domain.RefreshToken;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.domain.UserSystemRole;
import lat.nexofood.api.modules.identity.infrastructure.repository.RefreshTokenRepository;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lat.nexofood.api.modules.identity.web.dto.request.LoginRequest;
import lat.nexofood.api.modules.identity.web.dto.request.RefreshTokenRequest;
import lat.nexofood.api.modules.identity.web.dto.request.UserRegisterRequest;
import lat.nexofood.api.modules.identity.web.dto.response.AuthResponse;
import lat.nexofood.api.modules.identity.web.mapper.UserMapper;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lat.nexofood.api.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Spy
    private UserMapper userMapper = new UserMapper();

    private RegisterService registerService;
    private LoginService loginService;
    private RefreshTokenService refreshTokenService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        registerService = new RegisterService(userRepository, refreshTokenRepository, passwordEncoder, jwtService, userMapper);
        loginService = new LoginService(userRepository, refreshTokenRepository, passwordEncoder, jwtService, userMapper, tenantMemberRepository);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, jwtService, userMapper);

        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@nexofood.lat")
                .passwordHash("$2a$10$hashedpassword")
                .fullName("Juan Pérez")
                .phone("+51999888777")
                .systemRole(UserSystemRole.USER)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Debe registrar un nuevo usuario y generar tokens exitosamente")
    void shouldRegisterUserSuccessfully() {
        UserRegisterRequest request = new UserRegisterRequest(
                "test@nexofood.lat",
                "password123",
                "Juan Pérez",
                "+51999888777"
        );

        when(userRepository.existsByEmail("test@nexofood.lat")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedpassword");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtService.generateToken(sampleUser)).thenReturn("access-token-123");
        when(jwtService.generateRefreshToken(sampleUser)).thenReturn("refresh-token-123");
        when(jwtService.getRefreshExpirationTime()).thenReturn(604800000L);
        when(jwtService.getExpirationTime()).thenReturn(900000L);

        AuthResponse response = registerService.execute(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access-token-123");
        assertThat(response.refreshToken()).isEqualTo("refresh-token-123");
        assertThat(response.user().email()).isEqualTo("test@nexofood.lat");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Debe lanzar ResourceConflictException si el email ya existe al registrar")
    void shouldThrowConflictWhenEmailAlreadyExists() {
        UserRegisterRequest request = new UserRegisterRequest(
                "test@nexofood.lat",
                "password123",
                "Juan Pérez",
                null
        );

        when(userRepository.existsByEmail("test@nexofood.lat")).thenReturn(true);

        assertThatThrownBy(() -> registerService.execute(request))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage(ErrorMessages.EMAIL_ALREADY_EXISTS);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe iniciar sesión correctamente con credenciales válidas")
    void shouldLoginSuccessfully() {
        LoginRequest request = new LoginRequest("test@nexofood.lat", "password123");

        when(userRepository.findByEmail("test@nexofood.lat")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("password123", sampleUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(sampleUser)).thenReturn("access-token-123");
        when(jwtService.generateRefreshToken(sampleUser)).thenReturn("refresh-token-123");
        when(jwtService.getRefreshExpirationTime()).thenReturn(604800000L);
        when(jwtService.getExpirationTime()).thenReturn(900000L);

        AuthResponse response = loginService.execute(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access-token-123");
        verify(refreshTokenRepository).revokeAllUserTokens(sampleUser.getEmail());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Debe lanzar UnauthorizedException si la contraseña es incorrecta")
    void shouldThrowUnauthorizedWhenPasswordIsWrong() {
        LoginRequest request = new LoginRequest("test@nexofood.lat", "wrong-password");

        when(userRepository.findByEmail("test@nexofood.lat")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong-password", sampleUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> loginService.execute(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(ErrorMessages.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("Debe lanzar UnauthorizedException si el usuario está inactivo")
    void shouldThrowUnauthorizedWhenUserIsInactive() {
        sampleUser.setIsActive(false);
        LoginRequest request = new LoginRequest("test@nexofood.lat", "password123");

        when(userRepository.findByEmail("test@nexofood.lat")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("password123", sampleUser.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> loginService.execute(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(ErrorMessages.USER_INACTIVE);
    }

    @Test
    @DisplayName("Debe renovar token correctamente")
    void shouldRefreshTokenSuccessfully() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        RefreshToken tokenEntity = RefreshToken.builder()
                .id(UUID.randomUUID())
                .token("valid-refresh-token")
                .user(sampleUser)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken("valid-refresh-token")).thenReturn(Optional.of(tokenEntity));
        when(jwtService.generateToken(sampleUser)).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(sampleUser)).thenReturn("new-refresh-token");
        when(jwtService.getRefreshExpirationTime()).thenReturn(604800000L);
        when(jwtService.getExpirationTime()).thenReturn(900000L);

        AuthResponse response = refreshTokenService.execute(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(tokenEntity.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Debe detectar token revocado y lanzar UnauthorizedException revocando tokens")
    void shouldThrowUnauthorizedWhenTokenIsRevoked() {
        RefreshTokenRequest request = new RefreshTokenRequest("revoked-token");
        RefreshToken tokenEntity = RefreshToken.builder()
                .id(UUID.randomUUID())
                .token("revoked-token")
                .user(sampleUser)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(tokenEntity));

        assertThatThrownBy(() -> refreshTokenService.execute(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(ErrorMessages.TOKEN_REVOKED);

        verify(refreshTokenRepository).revokeAllUserTokens(sampleUser.getEmail());
    }
}
