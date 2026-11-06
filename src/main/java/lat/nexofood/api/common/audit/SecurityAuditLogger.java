package lat.nexofood.api.common.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registrador centralizado de eventos de auditoría de seguridad para acciones sensibles
 * (cambios de rol, bloqueos de clientes, cambios de precio, cancelaciones, conexión MP,
 * bloqueos de cuenta y cambios de contraseña).
 */
@Slf4j
@Component
public class SecurityAuditLogger {

    public void logEvent(AuditAction action, UUID tenantId, String targetId, String details) {
        String actor = resolveCurrentActor();
        logEventWithActor(action, actor, tenantId, targetId, details);
    }

    public void logEventWithActor(AuditAction action, String actor, UUID tenantId, String targetId, String details) {
        log.info(
                "[SECURITY_AUDIT] timestamp={} action={} actor={} tenantId={} targetId={} details=\"{}\"",
                OffsetDateTime.now(),
                action,
                actor != null ? actor : "SYSTEM/ANONYMOUS",
                tenantId != null ? tenantId : "GLOBAL",
                targetId != null ? targetId : "N/A",
                details != null ? details : ""
        );
    }

    private String resolveCurrentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
            return auth.getName();
        }
        return "SYSTEM/ANONYMOUS";
    }
}
