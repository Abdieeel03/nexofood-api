package lat.nexofood.api.modules.store.application.scheduler;

import lat.nexofood.api.common.config.CacheConfig;
import lat.nexofood.api.modules.notification.application.service.NotificationService;
import lat.nexofood.api.modules.notification.domain.NotificationType;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class StoreScheduleScheduler {

    private static final List<TenantStaffRole> MANAGEMENT_ROLES = List.of(
            TenantStaffRole.OWNER,
            TenantStaffRole.ADMIN
    );

    private static final List<TenantStaffRole> OPERATIONS_ROLES = List.of(
            TenantStaffRole.OWNER,
            TenantStaffRole.ADMIN,
            TenantStaffRole.COCINERO
    );

    private final TenantRepository tenantRepository;
    private final NotificationService notificationService;
    private final CacheManager cacheManager;

    @Value("${app.notifications.store-schedule-warning-minutes:15}")
    private int warningMinutes = 15;

    @Autowired
    public StoreScheduleScheduler(
            TenantRepository tenantRepository,
            @Autowired(required = false) NotificationService notificationService,
            @Autowired(required = false) CacheManager cacheManager
    ) {
        this.tenantRepository = tenantRepository;
        this.notificationService = notificationService;
        this.cacheManager = cacheManager;
    }

    public StoreScheduleScheduler(
            TenantRepository tenantRepository,
            NotificationService notificationService
    ) {
        this(tenantRepository, notificationService, null);
    }

    @Scheduled(cron = "${app.store.schedule-cron:0 * * * * *}")
    @Transactional
    public int processStoreSchedules() {
        return processStoreSchedules(LocalTime.now(), LocalDate.now());
    }

    @Transactional
    public int processStoreSchedules(LocalTime currentTime, LocalDate currentDate) {
        List<Tenant> tenants = tenantRepository.findAllByIsActiveTrueAndOpeningTimeIsNotNullAndClosingTimeIsNotNull();
        int updatedCount = 0;

        for (Tenant tenant : tenants) {
            if (tenant == null || tenant.getOpeningTime() == null || tenant.getClosingTime() == null) {
                continue;
            }

            boolean shouldBeOpen = tenant.isWithinOperatingHours(currentTime);

            if (!shouldBeOpen && !Boolean.FALSE.equals(tenant.getIsOpen())) {
                tenant.setIsOpen(false);
                tenantRepository.save(tenant);
                evictStorefrontCaches(tenant.getSlug());
                updatedCount++;
                log.info("Tienda '{}' ({}) cerrada automáticamente fuera de horario ({}-{})",
                        tenant.getName(), tenant.getId(), tenant.getOpeningTime(), tenant.getClosingTime());

                Map<String, Object> metadata = buildScheduleMetadata(tenant, false, null);
                notifyStaff(
                        tenant,
                        MANAGEMENT_ROLES,
                        NotificationType.STORE_AUTO_CLOSED,
                        "Tienda cerrada automáticamente: " + tenant.getName(),
                        "El restaurante '" + tenant.getName() + "' se cerró automáticamente al finalizar su horario ("
                                + tenant.getClosingTime() + ").",
                        "STORE_AUTO_CLOSED:" + tenant.getId() + ":" + currentDate + ":" + tenant.getClosingTime(),
                        metadata
                );
            } else if (shouldBeOpen && Boolean.FALSE.equals(tenant.getIsOpen())) {
                tenant.setIsOpen(true);
                tenantRepository.save(tenant);
                evictStorefrontCaches(tenant.getSlug());
                updatedCount++;
                log.info("Tienda '{}' ({}) abierta automáticamente dentro de horario ({}-{})",
                        tenant.getName(), tenant.getId(), tenant.getOpeningTime(), tenant.getClosingTime());

                Map<String, Object> metadata = buildScheduleMetadata(tenant, true, null);
                notifyStaff(
                        tenant,
                        MANAGEMENT_ROLES,
                        NotificationType.STORE_AUTO_OPENED,
                        "Tienda abierta automáticamente: " + tenant.getName(),
                        "El restaurante '" + tenant.getName() + "' se abrió automáticamente al iniciar su horario ("
                                + tenant.getOpeningTime() + ").",
                        "STORE_AUTO_OPENED:" + tenant.getId() + ":" + currentDate + ":" + tenant.getOpeningTime(),
                        metadata
                );
            }

            if (!tenant.getOpeningTime().equals(tenant.getClosingTime()) && warningMinutes > 0) {
                processProactiveWarnings(tenant, currentTime, currentDate, shouldBeOpen);
            }
        }

        return updatedCount;
    }

    public int getWarningMinutes() {
        return warningMinutes;
    }

    public void setWarningMinutes(int warningMinutes) {
        this.warningMinutes = warningMinutes;
    }

    private void processProactiveWarnings(
            Tenant tenant,
            LocalTime currentTime,
            LocalDate currentDate,
            boolean shouldBeOpen
    ) {
        if (!shouldBeOpen) {
            long mins = minutesUntil(currentTime, tenant.getOpeningTime());
            if (mins > 0 && mins <= warningMinutes) {
                Map<String, Object> metadata = buildScheduleMetadata(tenant, false, mins);
                notifyStaff(
                        tenant,
                        OPERATIONS_ROLES,
                        NotificationType.STORE_OPENING_SOON,
                        "Tu tienda abrirá pronto: " + tenant.getName(),
                        "El restaurante '" + tenant.getName() + "' abrirá automáticamente en "
                                + mins + " minuto(s) (" + tenant.getOpeningTime() + ").",
                        "STORE_OPENING_SOON:" + tenant.getId() + ":" + currentDate + ":" + tenant.getOpeningTime(),
                        metadata
                );
            }
        } else {
            long mins = minutesUntil(currentTime, tenant.getClosingTime());
            if (mins > 0 && mins <= warningMinutes) {
                Map<String, Object> metadata = buildScheduleMetadata(tenant, true, mins);
                notifyStaff(
                        tenant,
                        OPERATIONS_ROLES,
                        NotificationType.STORE_CLOSING_SOON,
                        "Tu tienda cerrará pronto: " + tenant.getName(),
                        "El restaurante '" + tenant.getName() + "' cerrará automáticamente en "
                                + mins + " minuto(s) (" + tenant.getClosingTime() + ").",
                        "STORE_CLOSING_SOON:" + tenant.getId() + ":" + currentDate + ":" + tenant.getClosingTime(),
                        metadata
                );
            }
        }
    }

    private long minutesUntil(LocalTime currentTime, LocalTime targetTime) {
        long mins = Duration.between(currentTime, targetTime).toMinutes();
        if (mins < 0) {
            mins += 1440;
        }
        return mins;
    }

    private Map<String, Object> buildScheduleMetadata(Tenant tenant, boolean isOpen, Long minutesRemaining) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (tenant.getId() != null) {
            metadata.put("tenantId", tenant.getId().toString());
        }
        metadata.put("tenantName", tenant.getName());
        if (tenant.getOpeningTime() != null) {
            metadata.put("openingTime", tenant.getOpeningTime().toString());
        }
        if (tenant.getClosingTime() != null) {
            metadata.put("closingTime", tenant.getClosingTime().toString());
        }
        metadata.put("isOpen", isOpen);
        if (minutesRemaining != null) {
            metadata.put("minutesRemaining", minutesRemaining);
        }
        return metadata;
    }

    private void notifyStaff(
            Tenant tenant,
            Collection<TenantStaffRole> roles,
            NotificationType type,
            String title,
            String message,
            String dedupKey,
            Map<String, Object> metadata
    ) {
        if (notificationService == null) {
            return;
        }
        notificationService.notifyTenantStaff(
                tenant,
                roles,
                type,
                title,
                message,
                tenant.getId(),
                dedupKey,
                metadata
        );
    }

    private void evictStorefrontCaches(String slug) {
        if (cacheManager == null) {
            return;
        }
        evictCache(CacheConfig.CACHE_STOREFRONT_MENU, slug);
        evictCache(CacheConfig.CACHE_STOREFRONT_CATEGORIES, slug);
    }

    private void evictCache(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return;
        }
        if (key != null && !key.isBlank()) {
            cache.evict(key);
        } else {
            cache.clear();
        }
    }
}
