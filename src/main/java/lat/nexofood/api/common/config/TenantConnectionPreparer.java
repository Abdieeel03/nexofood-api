package lat.nexofood.api.common.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Componente que establece el contexto de tenant en la sesión PostgreSQL
 * para que las políticas de Row Level Security (RLS) filtren automáticamente.
 *
 * Uso: Invocar setTenantContext(tenantId) al inicio de cada transacción
 * que requiera aislamiento de datos por tenant.
 *
 * NOTA: SET LOCAL solo aplica durante la transacción actual.
 * Es seguro con connection pooling (HikariCP) porque el setting
 * se resetea automáticamente al hacer rollback o commit.
 */
@Slf4j
@Component
public class TenantConnectionPreparer {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Establece el tenant_id en la sesión PostgreSQL para activar RLS.
     * Debe llamarse dentro de una transacción activa.
     *
     * @param tenantId UUID del tenant activo en el request actual
     */
    @Transactional
    public void setTenantContext(UUID tenantId) {
        if (tenantId == null) {
            log.warn("Se intentó establecer un tenant context nulo. RLS no será aplicado.");
            return;
        }
        entityManager.createNativeQuery(
            "SET LOCAL app.current_tenant_id = '" + tenantId + "'"
        ).executeUpdate();
        log.debug("Tenant context establecido para RLS: {}", tenantId);
    }

    /**
     * Limpia el tenant context de la sesión actual.
     */
    @Transactional
    public void clearTenantContext() {
        entityManager.createNativeQuery(
            "SET LOCAL app.current_tenant_id = ''"
        ).executeUpdate();
    }
}
