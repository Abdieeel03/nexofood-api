package lat.nexofood.api.common.config;

import lat.nexofood.api.common.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Sincronizador transaccional que invoca {@link TenantConnectionPreparer} al inicio de cada
 * transacción activa cuando existe un {@code tenantId} en {@link TenantContext}.
 * <p>
 * Registra un {@link TransactionSynchronization} en el {@link TransactionSynchronizationManager}
 * para garantizar que el contexto RLS de PostgreSQL se configure una única vez por transacción
 * y se limpie adecuadamente al finalizar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantTransactionSynchronization {

    private static final String RLS_PREPARED_RESOURCE_KEY = "nexofood.rls.transaction.prepared";

    private final TenantConnectionPreparer tenantConnectionPreparer;

    /**
     * Prepara el contexto de tenant en la transacción actual si hay un {@code tenantId} en
     * {@link TenantContext} y aún no ha sido aplicado en la transacción activa.
     */
    public void prepareCurrentTransactionIfNeeded() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            tenantConnectionPreparer.setTenantContext(tenantId);
            return;
        }

        if (TransactionSynchronizationManager.hasResource(RLS_PREPARED_RESOURCE_KEY)) {
            return;
        }

        TransactionSynchronizationManager.bindResource(RLS_PREPARED_RESOURCE_KEY, tenantId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.hasResource(RLS_PREPARED_RESOURCE_KEY)) {
                    TransactionSynchronizationManager.unbindResource(RLS_PREPARED_RESOURCE_KEY);
                }
            }
        });

        log.debug("Aplicando contexto RLS para tenant {} en transacción activa", tenantId);
        tenantConnectionPreparer.setTenantContext(tenantId);
    }
}
