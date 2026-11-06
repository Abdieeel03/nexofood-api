package lat.nexofood.api.security.ratelimit;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthRateLimitFilterTest {

    @Test
    @DisplayName("Debe permitir peticiones dentro del límite y retornar 429 Too Many Requests al exceder la cuota")
    void shouldBlockRequestsWhenRateLimitExceeded() throws Exception {
        AuthRateLimitService rateLimitService = new AuthRateLimitService(3, 60);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(rateLimitService, true);
        FilterChain filterChain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setRemoteAddr("192.168.1.50");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            assertThat(response.getHeader("X-Rate-Limit-Remaining")).isNotNull();
        }

        // 4to intento desde la misma IP debe ser bloqueado con 429
        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        blockedRequest.setRemoteAddr("192.168.1.50");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();

        filter.doFilter(blockedRequest, blockedResponse, filterChain);

        assertThat(blockedResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(blockedResponse.getHeader("Retry-After")).isNotNull();
        assertThat(blockedResponse.getContentAsString()).contains("Demasiadas solicitudes de autenticación");
        verify(filterChain, times(3)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Debe aplicar rate limiting también a rutas contextuales /api/v1/store/{slug}/auth/*")
    void shouldApplyRateLimitToStoreAuthEndpoints() throws Exception {
        AuthRateLimitService rateLimitService = new AuthRateLimitService(1, 60);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(rateLimitService, true);
        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/v1/store/burger-bros/auth/login");
        req1.setRemoteAddr("10.0.0.15");
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilter(req1, res1, filterChain);
        assertThat(res1.getStatus()).isEqualTo(HttpStatus.OK.value());

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/v1/store/burger-bros/auth/register");
        req2.setRemoteAddr("10.0.0.15");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(req2, res2, filterChain);
        assertThat(res2.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }
}
