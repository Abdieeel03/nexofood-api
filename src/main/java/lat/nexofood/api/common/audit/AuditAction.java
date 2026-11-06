package lat.nexofood.api.common.audit;

/**
 * Catálogo de acciones sensibles auditadas en el sistema.
 */
public enum AuditAction {
    ROLE_CHANGE,
    CUSTOMER_BLOCK,
    CUSTOMER_UNBLOCK,
    PRICE_CHANGE,
    ORDER_CANCELLATION,
    MP_CONNECTION,
    PASSWORD_CHANGE,
    ACCOUNT_LOCKOUT,
    ACCOUNT_DELETED
}
