package lat.nexofood.api.common.security;

import java.util.UUID;

/**
 * Almacena el identificador del tenant activo en el hilo de ejecución actual (ThreadLocal)
 * para propagarlo hacia la sesión de PostgreSQL (Row Level Security).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
