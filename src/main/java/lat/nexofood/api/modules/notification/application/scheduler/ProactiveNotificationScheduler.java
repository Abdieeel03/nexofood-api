package lat.nexofood.api.modules.notification.application.scheduler;

import lat.nexofood.api.common.config.TenantConnectionPreparer;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.notification.application.service.NotificationService;
import lat.nexofood.api.modules.notification.domain.NotificationActionStatus;
import lat.nexofood.api.modules.notification.domain.NotificationType;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.subscription.domain.Subscription;
import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import lat.nexofood.api.modules.subscription.infrastructure.repository.SubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ProactiveNotificationScheduler {

    private static final List<SubscriptionStatus> EXPIRING_ALERT_STATUSES = List.of(
            SubscriptionStatus.TRIAL,
            SubscriptionStatus.ACTIVE
    );

    private final TenantRepository tenantRepository;
    private final ProductRepository productRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final NotificationService notificationService;
    private final TenantConnectionPreparer tenantConnectionPreparer;

    @Value("${app.notifications.low-stock-threshold:5}")
    private int lowStockThreshold = 5;

    @Autowired
    public ProactiveNotificationScheduler(
            TenantRepository tenantRepository,
            ProductRepository productRepository,
            SubscriptionRepository subscriptionRepository,
            NotificationService notificationService,
            @Autowired(required = false) TenantConnectionPreparer tenantConnectionPreparer
    ) {
        this.tenantRepository = tenantRepository;
        this.productRepository = productRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.notificationService = notificationService;
        this.tenantConnectionPreparer = tenantConnectionPreparer;
    }

    public ProactiveNotificationScheduler(
            TenantRepository tenantRepository,
            ProductRepository productRepository,
            SubscriptionRepository subscriptionRepository,
            NotificationService notificationService
    ) {
        this(tenantRepository, productRepository, subscriptionRepository, notificationService, null);
    }

    @Scheduled(cron = "${app.notifications.daily-check-cron:0 0 8 * * *}")
    @Transactional
    public void checkLowStockAndExpiringSubscriptions() {
        checkLowStockProducts();
        checkExpiringSubscriptions();
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    private void checkLowStockProducts() {
        List<Tenant> tenants = tenantRepository.findAll();
        for (Tenant tenant : tenants) {
            if (tenant == null || tenant.getId() == null || !Boolean.TRUE.equals(tenant.getIsActive())) {
                continue;
            }
            if (tenantConnectionPreparer != null) {
                tenantConnectionPreparer.setTenantContext(tenant.getId());
            }
            List<Product> availableProducts = productRepository.findAllByTenantIdAndIsAvailableTrue(tenant.getId());
            for (Product product : availableProducts) {
                if (product.getStock() != null && product.getStock() <= lowStockThreshold) {
                    notificationService.checkAndNotifyProductStock(product);
                }
            }
        }
    }

    private void checkExpiringSubscriptions() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime horizon = now.plusDays(3);
        LocalDate today = LocalDate.now();

        List<Subscription> candidateSubscriptions = subscriptionRepository
                .findAllByStatusInAndEndDateBefore(EXPIRING_ALERT_STATUSES, horizon.plusSeconds(1));

        for (Subscription subscription : candidateSubscriptions) {
            if (subscription.getEndDate() == null
                    || subscription.getEndDate().isBefore(now)
                    || subscription.getEndDate().isAfter(horizon)) {
                continue;
            }

            tenantRepository.findBySubscriptionId(subscription.getId()).ifPresent(tenant -> {
                User owner = tenant.getOwner() != null ? tenant.getOwner() : subscription.getUser();
                if (owner == null || owner.getId() == null) {
                    return;
                }

                long daysRemaining = Math.max(0, Duration.between(now, subscription.getEndDate()).toDays());
                String planName = subscription.getPlan() != null ? subscription.getPlan().getName() : "actual";
                String dedupKey = "SUB_EXPIRING:" + subscription.getId() + ":" + today;
                String title = "Tu suscripción está por vencer";
                String message = "La suscripción (" + planName + ") de '" + tenant.getName()
                        + "' vence en " + daysRemaining + " día(s) ("
                        + subscription.getEndDate().toLocalDate()
                        + "). Renuévala para evitar interrupciones.";

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("subscriptionId", subscription.getId().toString());
                metadata.put("status", subscription.getStatus().name());
                metadata.put("endDate", subscription.getEndDate().toString());
                metadata.put("daysRemaining", daysRemaining);
                if (subscription.getPlan() != null) {
                    metadata.put("planName", subscription.getPlan().getName());
                }

                notificationService.createNotification(
                        owner,
                        tenant,
                        NotificationType.SUBSCRIPTION_EXPIRING_SOON,
                        title,
                        message,
                        false,
                        NotificationActionStatus.NONE,
                        subscription.getId(),
                        dedupKey,
                        metadata
                );
            });
        }
    }
}
