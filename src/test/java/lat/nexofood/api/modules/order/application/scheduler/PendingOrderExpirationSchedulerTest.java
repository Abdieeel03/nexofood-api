package lat.nexofood.api.modules.order.application.scheduler;

import lat.nexofood.api.common.security.TenantContext;
import lat.nexofood.api.modules.order.application.service.OrderService;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingOrderExpirationSchedulerTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PendingOrderExpirationScheduler scheduler;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Debe iterar tenants con TenantContext y liberar órdenes pendientes expiradas")
    void shouldReleaseExpiredPendingOrdersAcrossTenants() {
        ReflectionTestUtils.setField(scheduler, "pendingExpirationMinutes", 5L);

        UUID tenant1Id = UUID.randomUUID();
        UUID tenant2Id = UUID.randomUUID();
        Tenant t1 = Tenant.builder().id(tenant1Id).build();
        Tenant t2 = Tenant.builder().id(tenant2Id).build();

        when(tenantRepository.findAll()).thenReturn(List.of(t1, t2));
        when(orderService.releaseExpiredPendingOrdersForTenant(eq(tenant1Id), any(OffsetDateTime.class))).thenReturn(2);
        when(orderService.releaseExpiredPendingOrdersForTenant(eq(tenant2Id), any(OffsetDateTime.class))).thenReturn(1);

        int released = scheduler.releaseExpiredPendingOrders();

        assertThat(released).isEqualTo(3);
        assertThat(TenantContext.getTenantId()).isNull();
        verify(orderService).releaseExpiredPendingOrdersForTenant(eq(tenant1Id), any(OffsetDateTime.class));
        verify(orderService).releaseExpiredPendingOrdersForTenant(eq(tenant2Id), any(OffsetDateTime.class));
    }
}
