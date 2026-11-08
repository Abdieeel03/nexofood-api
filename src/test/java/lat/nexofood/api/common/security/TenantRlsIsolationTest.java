package lat.nexofood.api.common.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.servlet.FilterChain;
import lat.nexofood.api.common.config.TenantConnectionPreparer;
import lat.nexofood.api.common.config.TenantTransactionSynchronization;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantRlsIsolationTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query nativeQuery;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("TenantContextFilter debe poblar TenantContext desde tenantId en el path y limpiarlo al finalizar")
    void shouldPopulateTenantContextFromTenantIdPathAndClearAfterward() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContextFilter filter = new TenantContextFilter(tenantRepository);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tenants/" + tenantId + "/inventory/items");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<UUID> capturedDuringRequest = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedDuringRequest.set(TenantContext.getTenantId());

        filter.doFilter(request, response, chain);

        assertThat(capturedDuringRequest.get()).isEqualTo(tenantId);
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    @DisplayName("TenantContextFilter debe resolver el tenantId a partir del slug de la tienda en rutas /api/v1/store/{slug}/**")
    void shouldResolveTenantContextFromStoreSlugPath() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).slug("la-pizzeria").build();
        when(tenantRepository.findBySlug("la-pizzeria")).thenReturn(Optional.of(tenant));

        TenantContextFilter filter = new TenantContextFilter(tenantRepository);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/store/la-pizzeria/menu");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<UUID> capturedDuringRequest = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedDuringRequest.set(TenantContext.getTenantId());

        filter.doFilter(request, response, chain);

        assertThat(capturedDuringRequest.get()).isEqualTo(tenantId);
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    @DisplayName("TenantConnectionPreparer debe usar SELECT set_config('app.current_tenant_id', :tenantId, true) parametrizado")
    void shouldUseParameterizedSetConfigQuery() {
        UUID tenantId = UUID.randomUUID();
        when(entityManager.createNativeQuery("SELECT set_config('app.current_tenant_id', :tenantId, true)"))
                .thenReturn(nativeQuery);
        when(nativeQuery.setParameter("tenantId", tenantId.toString())).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(tenantId.toString());

        TenantConnectionPreparer preparer = new TenantConnectionPreparer(entityManager);
        preparer.setTenantContext(tenantId);

        verify(entityManager).createNativeQuery("SELECT set_config('app.current_tenant_id', :tenantId, true)");
        verify(nativeQuery).setParameter("tenantId", tenantId.toString());
        verify(nativeQuery).getSingleResult();
    }

    @Test
    @DisplayName("TenantTransactionSynchronization debe invocar TenantConnectionPreparer una sola vez por transacción y aislar tenants distintos")
    void shouldPrepareRlsOncePerTransactionAndIsolateAcrossTransactions() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), anyString())).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn("ok");

        TenantConnectionPreparer preparer = new TenantConnectionPreparer(entityManager);
        TenantTransactionSynchronization synchronization = new TenantTransactionSynchronization(preparer);

        // Transacción 1 para Tenant A
        TenantContext.setTenantId(tenantA);
        TransactionSynchronizationManager.initSynchronization();
        synchronization.prepareCurrentTransactionIfNeeded();
        synchronization.prepareCurrentTransactionIfNeeded(); // segunda llamada en la misma transacción no debe duplicar

        verify(nativeQuery, times(1)).setParameter("tenantId", tenantA.toString());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clearSynchronization();
        TenantContext.clear();

        // Transacción 2 para Tenant B
        TenantContext.setTenantId(tenantB);
        TransactionSynchronizationManager.initSynchronization();
        synchronization.prepareCurrentTransactionIfNeeded();

        verify(nativeQuery, times(1)).setParameter("tenantId", tenantB.toString());
    }
}
