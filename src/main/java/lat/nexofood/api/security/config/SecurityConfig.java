package lat.nexofood.api.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lat.nexofood.api.common.response.ErrorResponse;
import lat.nexofood.api.common.security.TenantContextFilter;
import lat.nexofood.api.security.custom.CustomUserDetailsService;
import lat.nexofood.api.security.jwt.JwtAuthenticationFilter;
import lat.nexofood.api.security.ratelimit.AuthRateLimitFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    public static final String UNAUTHORIZED_GENERIC_MESSAGE = "No autorizado: token inválido, expirado o ausente";
    public static final String FORBIDDEN_GENERIC_MESSAGE = "Acceso denegado: no tiene permisos para realizar esta operación";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;
    private final CorsConfigurationSource corsConfigurationSource;
    private final ObjectMapper objectMapper;
    private final AuthRateLimitFilter authRateLimitFilter;
    private final TenantContextFilter tenantContextFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CustomUserDetailsService userDetailsService,
            CorsConfigurationSource corsConfigurationSource,
            ObjectMapper objectMapper
    ) {
        this(jwtAuthenticationFilter, userDetailsService, corsConfigurationSource, objectMapper, null, null);
    }

    @Autowired
    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CustomUserDetailsService userDetailsService,
            CorsConfigurationSource corsConfigurationSource,
            ObjectMapper objectMapper,
            @Autowired(required = false) AuthRateLimitFilter authRateLimitFilter,
            @Autowired(required = false) TenantContextFilter tenantContextFilter
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
        this.corsConfigurationSource = corsConfigurationSource;
        this.objectMapper = objectMapper;
        this.authRateLimitFilter = authRateLimitFilter;
        this.tenantContextFilter = tenantContextFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        )
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                        )
                )
                .authenticationProvider(authenticationProvider())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            Exception jwtEx = (Exception) request.getAttribute("jwt_exception");
                            if (jwtEx != null) {
                                log.warn("Authentication error (JWT): {}", jwtEx.getMessage());
                            } else {
                                log.warn("Authentication error: {}", authException.getMessage());
                            }
                            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                                    UNAUTHORIZED_GENERIC_MESSAGE, "UNAUTHORIZED", request.getRequestURI());
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            log.warn("Access denied error: {}", accessDeniedException.getMessage());
                            writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                                    FORBIDDEN_GENERIC_MESSAGE, "FORBIDDEN", request.getRequestURI());
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/test").permitAll()
                        // Actuator: health e info públicos para monitoreo; resto restringido a SUPERADMIN
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("SUPERADMIN")
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        // Identity: registro y login de plataforma (público)
                        .requestMatchers("/api/auth/**", "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        // Planes de suscripción SaaS (público)
                        .requestMatchers(HttpMethod.GET, "/api/v1/plans").permitAll()
                        // Tenants: datos públicos de la tienda para el storefront (sin token)
                        .requestMatchers(HttpMethod.GET, "/api/v1/tenants/slug/**").permitAll()
                        // Store: registro y login contextual de clientes (público)
                        .requestMatchers("/api/v1/store/*/auth/**").permitAll()
                        // Storefront: menú, categorías y productos públicos para clientes (sin token)
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/store/*/menu",
                                "/api/v1/store/*/categories",
                                "/api/v1/store/*/products",
                                "/api/v1/store/*/products/*"
                        ).permitAll()
                        .anyRequest().authenticated()
                );

        if (authRateLimitFilter != null) {
            http.addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        }
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        if (tenantContextFilter != null) {
            http.addFilterAfter(tenantContextFilter, JwtAuthenticationFilter.class);
        }

        return http.build();
    }

    private void writeErrorResponse(HttpServletResponse response, int status, String message, String error, String path)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = ErrorResponse.builder()
                .message(message)
                .error(error)
                .status(status)
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
