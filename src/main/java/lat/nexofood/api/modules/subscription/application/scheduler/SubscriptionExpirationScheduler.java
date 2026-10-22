package lat.nexofood.api.modules.subscription.application.scheduler;

import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.subscription.domain.Subscription;
import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import lat.nexofood.api.modules.subscription.infrastructure.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpirationScheduler {

    private static final List<SubscriptionStatus> EXPIRABLE_STATUSES = List.of(
            SubscriptionStatus.TRIAL,
            SubscriptionStatus.ACTIVE
    );

    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;

    @Scheduled(cron = "${nexofood.subscription.expiration-cron:0 0 0 * * *}")
    @Transactional
    public int processExpiredSubscriptions() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Subscription> expiredSubscriptions = subscriptionRepository
                .findAllByStatusInAndEndDateBefore(EXPIRABLE_STATUSES, now);

        if (expiredSubscriptions.isEmpty()) {
            return 0;
        }

        for (Subscription subscription : expiredSubscriptions) {
            SubscriptionStatus previousStatus = subscription.getStatus();
            SubscriptionStatus nextStatus = previousStatus == SubscriptionStatus.TRIAL
                    ? SubscriptionStatus.EXPIRED
                    : SubscriptionStatus.PAST_DUE;

            subscription.setStatus(nextStatus);
            subscriptionRepository.save(subscription);

            tenantRepository.findBySubscriptionId(subscription.getId()).ifPresent(tenant -> {
                if (Boolean.TRUE.equals(tenant.getIsActive())) {
                    tenant.setIsActive(false);
                    tenantRepository.save(tenant);
                    log.info("Restaurante {} desactivado automáticamente por vencimiento de suscripción {}",
                            tenant.getId(), subscription.getId());
                }
            });

            log.info("Suscripción {} pasó de {} a {} tras vencer el {}",
                    subscription.getId(), previousStatus, nextStatus, subscription.getEndDate());
        }

        return expiredSubscriptions.size();
    }
}
