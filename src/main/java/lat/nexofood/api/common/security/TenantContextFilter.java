package lat.nexofood.api.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interceptor/Filtro HTTP que extrae el {@code tenantId} o {@code slug} del path de la petición
 * y puebla {@link TenantContext} durante el ciclo de vida del request para activar RLS.
 */
@Slf4j
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Pattern TENANT_ID_PATH_PATTERN = Pattern.compile(
            "^/api/v1/(?:admin/)?tenants/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:/.*)?$"
    );

    private static final Pattern STORE_SLUG_PATH_PATTERN = Pattern.compile(
            "^/api/v1/(?:store|tenants/slug)/([a-zA-Z0-9_-]+)(?:/.*)?$"
    );

    private final TenantRepository tenantRepository;

    public TenantContextFilter(@Autowired(required = false) TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            UUID resolvedTenantId = resolveTenantId(request);
            if (resolvedTenantId != null) {
                TenantContext.setTenantId(resolvedTenantId);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private UUID resolveTenantId(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return null;
        }

        Matcher idMatcher = TENANT_ID_PATH_PATTERN.matcher(uri);
        if (idMatcher.matches()) {
            try {
                return UUID.fromString(idMatcher.group(1));
            } catch (IllegalArgumentException ex) {
                log.debug("UUID de tenant inválido en URI {}: {}", uri, ex.getMessage());
                return null;
            }
        }

        Matcher slugMatcher = STORE_SLUG_PATH_PATTERN.matcher(uri);
        if (slugMatcher.matches() && tenantRepository != null) {
            String slug = slugMatcher.group(1);
            return tenantRepository.findBySlug(slug)
                    .map(t -> t.getId())
                    .orElse(null);
        }

        return null;
    }
}
