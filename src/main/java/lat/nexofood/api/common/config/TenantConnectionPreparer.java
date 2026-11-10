package lat.nexofood.api.common.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Prepara la conexión de base de datos para aplicar las políticas de Row Level Security (RLS)
 * definidas en PostgreSQL (`current_setting('app.current_tenant_id', true)`).
 * <p>
 * Utiliza {@code SELECT set_config('app.current_tenant_id', :tenantId, true)} parametrizado
 * para evitar cualquier concatenación de SQL y aplicar el valor únicamente a la transacción actual.
 */
@Component
public class TenantConnectionPreparer {

    @PersistenceContext
    private EntityManager entityManager;

    public TenantConnectionPreparer() {
    }

    public TenantConnectionPreparer(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void setTenantContext(UUID tenantId) {
        if (tenantId != null && entityManager != null) {
            entityManager.createNativeQuery("SELECT set_config('app.current_tenant_id', :tenantId, true)")
                    .setParameter("tenantId", tenantId.toString())
                    .getSingleResult();
        }
    }
}
