package lat.nexofood.api.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import lat.nexofood.api.security.custom.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should authenticate active user with valid token and set authentication details")
    void testValidTokenAuthenticates() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UserDetails userDetails = User.builder()
                .username("user@nexofood.lat")
                .password("password")
                .roles("USER")
                .disabled(false)
                .build();

        when(jwtService.extractUsername("valid-token")).thenReturn("user@nexofood.lat");
        when(userDetailsService.loadUserByUsername("user@nexofood.lat")).thenReturn(userDetails);
        when(jwtService.isTokenValid("valid-token", userDetails)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("user@nexofood.lat", authentication.getName());
        assertNotNull(authentication.getDetails());
        assertInstanceOf(WebAuthenticationDetails.class, authentication.getDetails());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should NOT authenticate disabled or inactive user even with valid token")
    void testDisabledUserIsNotAuthenticated() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // User is disabled (isActive = false)
        UserDetails userDetails = User.builder()
                .username("disabled@nexofood.lat")
                .password("password")
                .roles("USER")
                .disabled(true)
                .build();

        assertFalse(userDetails.isEnabled(), "User should be disabled");

        when(jwtService.extractUsername("valid-token")).thenReturn("disabled@nexofood.lat");
        when(userDetailsService.loadUserByUsername("disabled@nexofood.lat")).thenReturn(userDetails);
        when(jwtService.isTokenValid("valid-token", userDetails)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        // Security check passed: user is disabled, so authentication must remain null
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "Disabled user must not be authenticated");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should catch JWT exceptions, clear security context, and set request attribute")
    void testExpiredTokenSetsRequestAttribute() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.extractUsername("expired-token")).thenThrow(new ExpiredJwtException(null, null, "Token expired"));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(request.getAttribute("jwt_exception"));
        verify(filterChain).doFilter(request, response);
    }
}
