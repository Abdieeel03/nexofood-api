package lat.nexofood.api.modules.order.application.scheduler;

import lat.nexofood.api.common.security.TenantContext;
import lat.nexofood.api.modules.order.application.service.OrderService;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Libera automáticamente órdenes en estado {@code PENDIENTE} sin pago aprobado
 * cuando superan el tiempo máximo de retención de stock (por defecto 5 minutos).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingOrderExpirationScheduler {

    private final TenantRepository tenantRepository;
    private final OrderService orderService;

    @Value("${app.orders.pending-expiration-minutes:5}")
    private long pendingExpirationMinutes;

    @Scheduled(cron = "${app.orders.pending-expiration-cron:0 * * * * *}")
    public int releaseExpiredPendingOrders() {
        long minutes = Math.max(1L, pendingExpirationMinutes);
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(minutes);
        List<Tenant> tenants = tenantRepository.findAll();

        int totalReleased = 0;
        for (Tenant tenant : tenants) {
            if (tenant == null || tenant.getId() == null) {
                continue;
            }
            try {
                TenantContext.setTenantId(tenant.getId());
                int released = orderService.releaseExpiredPendingOrdersForTenant(tenant.getId(), cutoff);
                totalReleased += released;
            } catch (Exception ex) {
                log.warn("Error liberando órdenes pendientes expiradas para tenant {}: {}",
                        tenant.getId(), ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }

        if (totalReleased > 0) {
            log.info("Órdenes pendientes expiradas liberadas automáticamente: {}", totalReleased);
        }
        return totalReleased;
    }
}
