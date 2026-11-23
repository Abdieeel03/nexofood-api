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
import lat.nexofood.api.modules.subscription.domain.SubscriptionPlan;
import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import lat.nexofood.api.modules.subscription.infrastructure.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProactiveNotificationSchedulerTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private TenantConnectionPreparer tenantConnectionPreparer;

    private ProactiveNotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ProactiveNotificationScheduler(
                tenantRepository,
                productRepository,
                subscriptionRepository,
                notificationService,
                tenantConnectionPreparer
        );
    }

    @Test
    @DisplayName("Verifica stock bajo en tiendas activas aplicando contexto RLS e ignora tiendas inactivas")
    void checkLowStockNotifiesOnlyActiveTenantsAndLowStockProducts() {
        UUID activeTenantId = UUID.randomUUID();
        UUID inactiveTenantId = UUID.randomUUID();

        Tenant activeTenant = Tenant.builder().id(activeTenantId).name("Tienda Activa").isActive(true).build();
        Tenant inactiveTenant = Tenant.builder().id(inactiveTenantId).name("Tienda Inactiva").isActive(false).build();

        Product lowProduct = Product.builder().id(UUID.randomUUID()).tenant(activeTenant).name("Salsa").stock(2).isAvailable(true).build();
        Product zeroProduct = Product.builder().id(UUID.randomUUID()).tenant(activeTenant).name("Pan").stock(0).isAvailable(true).build();
        Product highProduct = Product.builder().id(UUID.randomUUID()).tenant(activeTenant).name("Refresco").stock(20).isAvailable(true).build();

        when(tenantRepository.findAll()).thenReturn(List.of(activeTenant, inactiveTenant));
        when(productRepository.findAllByTenantIdAndIsAvailableTrue(activeTenantId))
                .thenReturn(List.of(lowProduct, zeroProduct, highProduct));
        when(subscriptionRepository.findAllByStatusInAndEndDateBefore(anyList(), any())).thenReturn(List.of());

        scheduler.checkLowStockAndExpiringSubscriptions();

        verify(tenantConnectionPreparer).setTenantContext(activeTenantId);
        verify(tenantConnectionPreparer, never()).setTenantContext(inactiveTenantId);
        verify(productRepository, never()).findAllByTenantIdAndIsAvailableTrue(inactiveTenantId);

        verify(notificationService).checkAndNotifyProductStock(lowProduct);
        verify(notificationService).checkAndNotifyProductStock(zeroProduct);
        verify(notificationService, never()).checkAndNotifyProductStock(highProduct);
    }

    @Test
    @DisplayName("Notifica suscripciones TRIAL o ACTIVE por vencer en <= 3 días con dedupKey diario")
    void checkExpiringSubscriptionsNotifiesWithinThreeDaysWindow() {
        when(tenantRepository.findAll()).thenReturn(List.of());

        OffsetDateTime now = OffsetDateTime.now();
        User owner1 = User.builder().id(UUID.randomUUID()).email("owner1@nexofood.lat").fullName("Owner 1").build();
        User owner2 = User.builder().id(UUID.randomUUID()).email("owner2@nexofood.lat").fullName("Owner 2").build();

        SubscriptionPlan proPlan = SubscriptionPlan.builder().id(UUID.randomUUID()).name("Plan Pro").build();

        Subscription expiringActive = Subscription.builder()
                .id(UUID.randomUUID())
                .user(owner1)
                .plan(proPlan)
                .status(SubscriptionStatus.ACTIVE)
                .endDate(now.plusDays(2))
                .build();

        Subscription expiringTrial = Subscription.builder()
                .id(UUID.randomUUID())
                .user(owner2)
                .plan(proPlan)
                .status(SubscriptionStatus.TRIAL)
                .endDate(now.plusHours(10))
                .build();

        Subscription alreadyExpired = Subscription.builder()
                .id(UUID.randomUUID())
                .user(owner1)
                .status(SubscriptionStatus.ACTIVE)
                .endDate(now.minusDays(1))
                .build();

        Subscription farFuture = Subscription.builder()
                .id(UUID.randomUUID())
                .user(owner2)
                .status(SubscriptionStatus.ACTIVE)
                .endDate(now.plusDays(10))
                .build();

        Tenant tenant1 = Tenant.builder().id(UUID.randomUUID()).name("Restaurante 1").owner(owner1).subscription(expiringActive).build();
        Tenant tenant2 = Tenant.builder().id(UUID.randomUUID()).name("Restaurante 2").owner(owner2).subscription(expiringTrial).build();

        when(subscriptionRepository.findAllByStatusInAndEndDateBefore(
                eq(List.of(SubscriptionStatus.TRIAL, SubscriptionStatus.ACTIVE)),
                any(OffsetDateTime.class)
        )).thenReturn(List.of(expiringActive, expiringTrial, alreadyExpired, farFuture));

        when(tenantRepository.findBySubscriptionId(expiringActive.getId())).thenReturn(Optional.of(tenant1));
        when(tenantRepository.findBySubscriptionId(expiringTrial.getId())).thenReturn(Optional.of(tenant2));

        scheduler.checkLowStockAndExpiringSubscriptions();

        LocalDate today = LocalDate.now();
        verify(notificationService).createNotification(
                eq(owner1),
                eq(tenant1),
                eq(NotificationType.SUBSCRIPTION_EXPIRING_SOON),
                anyString(),
                anyString(),
                eq(false),
                eq(NotificationActionStatus.NONE),
                eq(expiringActive.getId()),
                eq("SUB_EXPIRING:" + expiringActive.getId() + ":" + today),
                anyMap()
        );

        verify(notificationService).createNotification(
                eq(owner2),
                eq(tenant2),
                eq(NotificationType.SUBSCRIPTION_EXPIRING_SOON),
                anyString(),
                anyString(),
                eq(false),
                eq(NotificationActionStatus.NONE),
                eq(expiringTrial.getId()),
                eq("SUB_EXPIRING:" + expiringTrial.getId() + ":" + today),
                anyMap()
        );

        verify(tenantRepository, never()).findBySubscriptionId(alreadyExpired.getId());
        verify(tenantRepository, never()).findBySubscriptionId(farFuture.getId());
    }
}
