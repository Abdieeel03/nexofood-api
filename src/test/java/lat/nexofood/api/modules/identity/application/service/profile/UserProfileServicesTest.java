package lat.nexofood.api.modules.identity.application.service.profile;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.RefreshTokenRepository;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lat.nexofood.api.modules.identity.web.dto.request.ChangePasswordRequest;
import lat.nexofood.api.modules.identity.web.dto.request.UserUpdateRequest;
import lat.nexofood.api.modules.identity.web.dto.response.UserResponse;
import lat.nexofood.api.modules.identity.web.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServicesTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private UserMapper userMapper = new UserMapper();

    private UpdateProfileService updateProfileService;
    private ChangePasswordService changePasswordService;
    private DeleteMyAccountService deleteMyAccountService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        updateProfileService = new UpdateProfileService(userRepository, currentUserProvider, userMapper);
        changePasswordService = new ChangePasswordService(userRepository, refreshTokenRepository, currentUserProvider, passwordEncoder);
        deleteMyAccountService = new DeleteMyAccountService(userRepository, refreshTokenRepository, currentUserProvider);

        currentUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@nexofood.lat")
                .passwordHash("$2a$10$oldHash")
                .fullName("Nombre Antiguo")
                .phone("900000000")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("UpdateProfileService actualiza nombre y teléfono del usuario actual")
    void updatesProfileSuccessfully() {
        when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserUpdateRequest request = new UserUpdateRequest("Nombre Nuevo", "+51999111222");
        UserResponse response = updateProfileService.execute(request);

        assertEquals("Nombre Nuevo", response.fullName());
        assertEquals("+51999111222", response.phone());
    }

    @Test
    @DisplayName("ChangePasswordService verifica contraseña actual, guarda el nuevo hash y revoca todos los tokens")
    void changesPasswordAndRevokesAllTokens() {
        when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(passwordEncoder.matches("oldPass123", "$2a$10$oldHash")).thenReturn(true);
        when(passwordEncoder.matches("newPass456", "$2a$10$oldHash")).thenReturn(false);
        when(passwordEncoder.encode("newPass456")).thenReturn("$2a$10$newHash");

        ChangePasswordRequest request = new ChangePasswordRequest("oldPass123", "newPass456");
        changePasswordService.execute(request);

        assertEquals("$2a$10$newHash", currentUser.getPasswordHash());
        verify(userRepository).save(currentUser);
        verify(refreshTokenRepository).revokeAllUserTokens("user@nexofood.lat");
    }

    @Test
    @DisplayName("ChangePasswordService falla si la contraseña actual es incorrecta")
    void changePasswordThrowsWhenCurrentPasswordWrong() {
        when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(passwordEncoder.matches("wrongPass", "$2a$10$oldHash")).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest("wrongPass", "newPass456");
        assertThrows(BadRequestException.class, () -> changePasswordService.execute(request));
        verify(refreshTokenRepository, never()).revokeAllUserTokens(any());
    }

    @Test
    @DisplayName("DeleteMyAccountService aplica soft delete (deletedAt + isActive=false) y revoca tokens")
    void deletesMyAccountSoftly() {
        when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);

        deleteMyAccountService.execute();

        assertFalse(currentUser.getIsActive());
        assertNotNull(currentUser.getDeletedAt());
        verify(userRepository).save(currentUser);
        verify(refreshTokenRepository).revokeAllUserTokens("user@nexofood.lat");
    }
}
