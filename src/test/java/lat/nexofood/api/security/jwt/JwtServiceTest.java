package lat.nexofood.api.security.jwt;

import io.jsonwebtoken.security.WeakKeyException;
import lat.nexofood.api.modules.auth.domain.User;
import lat.nexofood.api.modules.auth.domain.UserSystemRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    // Minimum 256 bits (32 bytes) for HMAC-SHA256
    private final String validSecret = "abcdefghijklmnopqrstuvwxyz1234567890ABCDEF";
    private final String validRefreshSecret = "9876543210zyxwvutsrqponmlkjihgfedcbaFEDCBA";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", validSecret);
        ReflectionTestUtils.setField(jwtService, "refreshSecretKey", validRefreshSecret);
        ReflectionTestUtils.setField(jwtService, "expirationTime", 60000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpirationTime", 120000L);
    }

    @Test
    @DisplayName("Should generate valid token with claims and extract username, userId, and role")
    void testGenerateAndExtractToken() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@nexofood.lat")
                .fullName("Test User")
                .systemRole(UserSystemRole.USER)
                .isActive(true)
                .build();

        String token = jwtService.generateToken(user);
        assertNotNull(token);

        String username = jwtService.extractUsername(token);
        assertEquals("user@nexofood.lat", username);

        String extractedUserId = jwtService.extractUserId(token);
        assertEquals(userId.toString(), extractedUserId);

        String role = jwtService.extractRole(token);
        assertEquals("USER", role);

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .builder()
                .username("user@nexofood.lat")
                .password("password")
                .roles("USER")
                .build();

        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    @DisplayName("Should fail when secret key is weaker than 256 bits")
    void testWeakKeyFails() {
        ReflectionTestUtils.setField(jwtService, "secretKey", "short_key");
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@nexofood.lat")
                .systemRole(UserSystemRole.USER)
                .build();

        assertThrows(WeakKeyException.class, () -> jwtService.generateToken(user));
    }

    @Test
    @DisplayName("Expired token should return false safely in isTokenValid")
    void testExpiredTokenReturnsFalseSafely() {
        ReflectionTestUtils.setField(jwtService, "expirationTime", -1000L); // already expired
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@nexofood.lat")
                .systemRole(UserSystemRole.USER)
                .build();

        String token = jwtService.generateToken(user);
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .builder()
                .username("user@nexofood.lat")
                .password("password")
                .roles("USER")
                .build();

        // Expired token safely returns false instead of throwing exception
        assertFalse(jwtService.isTokenValid(token, userDetails));
    }
}
