package lat.nexofood.api.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Filtro HTTP que aplica Rate Limiting (Bucket4j) sobre los endpoints críticos de autenticación:
 * {@code /api/v1/auth/login}, {@code /api/v1/auth/register}, {@code /api/v1/auth/refresh}
 * y {@code /api/v1/store/{slug}/auth/*}.
 */
@Slf4j
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Pattern GLOBAL_AUTH_PATTERN = Pattern.compile("^/api/v1/auth/(login|register|refresh)$");
    private static final Pattern STORE_AUTH_PATTERN = Pattern.compile("^/api/v1/store/[^/]+/auth/(login|register)$");

    private final AuthRateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Value("${app.security.rate-limit.enabled:true}")
    private boolean enabled = true;

    @Autowired
    public AuthRateLimitFilter(AuthRateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public AuthRateLimitFilter(AuthRateLimitService rateLimitService, boolean enabled) {
        this(rateLimitService);
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!enabled || !isRateLimitedEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        String bucketKey = clientIp + ":" + normalizeEndpointGroup(request.getRequestURI());

        ConsumptionProbe probe = rateLimitService.tryConsume(bucketKey);
        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        long waitForRefillSeconds = Math.max(1L, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        log.warn("Rate limit excedido desde IP {} hacia {}", clientIp, request.getRequestURI());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(waitForRefillSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .message(ErrorMessages.RATE_LIMIT_EXCEEDED)
                .path(request.getRequestURI())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    boolean isRateLimitedEndpoint(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        return GLOBAL_AUTH_PATTERN.matcher(uri).matches()
                || STORE_AUTH_PATTERN.matcher(uri).matches();
    }

    private String normalizeEndpointGroup(String uri) {
        if (STORE_AUTH_PATTERN.matcher(uri).matches()) {
            return "/api/v1/store/*/auth";
        }
        return uri;
    }

    private String extractClientIp(HttpServletRequest request) {
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isBlank()) {
            return cfConnectingIp.trim();
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "unknown";
    }
}
