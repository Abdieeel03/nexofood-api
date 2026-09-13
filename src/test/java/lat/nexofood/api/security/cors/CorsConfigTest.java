package lat.nexofood.api.security.cors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {

    @Test
    @DisplayName("Should configure CORS correctly with allowed origin patterns")
    void testCorsConfigurationSource() {
        CorsConfig corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "allowedOrigins", new String[]{"http://localhost:3000", "http://localhost:5173"});
        ReflectionTestUtils.setField(corsConfig, "allowedMethods", new String[]{"GET", "POST", "PUT", "DELETE"});
        ReflectionTestUtils.setField(corsConfig, "allowedHeaders", new String[]{"Authorization", "Content-Type"});
        ReflectionTestUtils.setField(corsConfig, "exposedHeaders", new String[]{"Authorization"});
        ReflectionTestUtils.setField(corsConfig, "allowCredentials", true);
        ReflectionTestUtils.setField(corsConfig, "maxAge", 3600L);

        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        assertNotNull(source);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("Origin", "http://localhost:3000");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config);
        assertEquals(2, config.getAllowedOriginPatterns().size());
        assertEquals("http://localhost:3000", config.checkOrigin("http://localhost:3000"));
        assertTrue(config.getAllowCredentials());
    }

    @Test
    @DisplayName("Allowed origin patterns safely allow wildcard * with allowCredentials=true")
    void testWildcardPatternWithCredentialsSucceeds() {
        CorsConfig corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "allowedOrigins", new String[]{"*"});
        ReflectionTestUtils.setField(corsConfig, "allowedMethods", new String[]{"GET", "POST"});
        ReflectionTestUtils.setField(corsConfig, "allowedHeaders", new String[]{"*"});
        ReflectionTestUtils.setField(corsConfig, "exposedHeaders", new String[]{});
        ReflectionTestUtils.setField(corsConfig, "allowCredentials", true);
        ReflectionTestUtils.setField(corsConfig, "maxAge", 3600L);

        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("Origin", "http://localhost:3000");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config);
        // With allowedOriginPatterns, checkOrigin returns the exact origin rather than throwing an exception:
        assertEquals("http://localhost:3000", config.checkOrigin("http://localhost:3000"));
        assertTrue(config.getAllowCredentials());
    }
}
