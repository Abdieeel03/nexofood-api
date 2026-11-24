package lat.nexofood.api.modules.store.application.scheduler;

import lat.nexofood.api.common.config.CacheConfig;
import lat.nexofood.api.modules.notification.application.service.NotificationService;
import lat.nexofood.api.modules.notification.domain.NotificationType;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreScheduleSchedulerTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache menuCache;

    @Mock
    private Cache categoriesCache;

    private StoreScheduleScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new StoreScheduleScheduler(tenantRepository, notificationService, cacheManager);
        scheduler.setWarningMinutes(15);
    }

    @Test
    @DisplayName("Cierra automáticamente la tienda al pasar closingTime, limpia caché y notifica STORE_AUTO_CLOSED")
    void autoClosesStorePastClosingTime() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name("Burger Bros")
                .slug("burger-bros")
                .isActive(true)
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(22, 0))
                .isOpen(true)
                .build();

        when(tenantRepository.findAllByIsActiveTrueAndOpeningTimeIsNotNullAndClosingTimeIsNotNull())
                .thenReturn(List.of(tenant));
        when(cacheManager.getCache(CacheConfig.CACHE_STOREFRONT_MENU)).thenReturn(menuCache);
        when(cacheManager.getCache(CacheConfig.CACHE_STOREFRONT_CATEGORIES)).thenReturn(categoriesCache);

        LocalDate today = LocalDate.of(2026, 11, 22);
        int updated = scheduler.processStoreSchedules(LocalTime.of(22, 1), today);

        assertEquals(1, updated);
        assertFalse(tenant.getIsOpen());
        verify(tenantRepository).save(tenant);
        verify(menuCache).evict("burger-bros");
        verify(categoriesCache).evict("burger-bros");
        verify(notificationService).notifyTenantStaff(
                eq(tenant),
                eq(List.of(TenantStaffRole.OWNER, TenantStaffRole.ADMIN)),
                eq(NotificationType.STORE_AUTO_CLOSED),
                anyString(),
                anyString(),
                eq(tenantId),
                eq("STORE_AUTO_CLOSED:" + tenantId + ":" + today + ":22:00"),
                anyMap()
        );
    }

    @Test
    @DisplayName("Abre automáticamente la tienda dentro de horario cuando estaba cerrada y notifica STORE_AUTO_OPENED")
    void autoOpensStoreWithinSchedule() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name("Burger Bros")
                .slug("burger-bros")
                .isActive(true)
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(22, 0))
                .isOpen(false)
                .build();

        when(tenantRepository.findAllByIsActiveTrueAndOpeningTimeIsNotNullAndClosingTimeIsNotNull())
                .thenReturn(List.of(tenant));
        when(cacheManager.getCache(CacheConfig.CACHE_STOREFRONT_MENU)).thenReturn(menuCache);
        when(cacheManager.getCache(CacheConfig.CACHE_STOREFRONT_CATEGORIES)).thenReturn(categoriesCache);

        LocalDate today = LocalDate.of(2026, 11, 22);
        int updated = scheduler.processStoreSchedules(LocalTime.of(9, 0), today);

        assertEquals(1, updated);
        assertTrue(tenant.getIsOpen());
        verify(tenantRepository).save(tenant);
        verify(menuCache).evict("burger-bros");
        verify(categoriesCache).evict("burger-bros");
        verify(notificationService).notifyTenantStaff(
                eq(tenant),
                eq(List.of(TenantStaffRole.OWNER, TenantStaffRole.ADMIN)),
                eq(NotificationType.STORE_AUTO_OPENED),
                anyString(),
                anyString(),
                eq(tenantId),
                eq("STORE_AUTO_OPENED:" + tenantId + ":" + today + ":09:00"),
                anyMap()
        );
    }

    @Test
    @DisplayName("Envía aviso proactivo STORE_OPENING_SOON antes de la apertura")
    void sendsOpeningSoonWarning() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name("Cafe Sol")
                .slug("cafe-sol")
                .isActive(true)
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(20, 0))
                .isOpen(false)
                .build();

        when(tenantRepository.findAllByIsActiveTrueAndOpeningTimeIsNotNullAndClosingTimeIsNotNull())
                .thenReturn(List.of(tenant));

        LocalDate today = LocalDate.of(2026, 11, 22);
        int updated = scheduler.processStoreSchedules(LocalTime.of(7, 50), today);

        assertEquals(0, updated);
        verify(tenantRepository, never()).save(any());
        verify(notificationService).notifyTenantStaff(
                eq(tenant),
                eq(List.of(TenantStaffRole.OWNER, TenantStaffRole.ADMIN, TenantStaffRole.COCINERO)),
                eq(NotificationType.STORE_OPENING_SOON),
                anyString(),
                anyString(),
                eq(tenantId),
                eq("STORE_OPENING_SOON:" + tenantId + ":" + today + ":08:00"),
                anyMap()
        );
    }

    @Test
    @DisplayName("Envía aviso proactivo STORE_CLOSING_SOON antes del cierre")
    void sendsClosingSoonWarning() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name("Pizza Nexo")
                .slug("pizza-nexo")
                .isActive(true)
                .openingTime(LocalTime.of(10, 0))
                .closingTime(LocalTime.of(22, 0))
                .isOpen(true)
                .build();

        when(tenantRepository.findAllByIsActiveTrueAndOpeningTimeIsNotNullAndClosingTimeIsNotNull())
                .thenReturn(List.of(tenant));

        LocalDate today = LocalDate.of(2026, 11, 22);
        int updated = scheduler.processStoreSchedules(LocalTime.of(21, 50), today);

        assertEquals(0, updated);
        verify(tenantRepository, never()).save(any());
        verify(notificationService).notifyTenantStaff(
                eq(tenant),
                eq(List.of(TenantStaffRole.OWNER, TenantStaffRole.ADMIN, TenantStaffRole.COCINERO)),
                eq(NotificationType.STORE_CLOSING_SOON),
                anyString(),
                anyString(),
                eq(tenantId),
                eq("STORE_CLOSING_SOON:" + tenantId + ":" + today + ":22:00"),
                anyMap()
        );
    }

    @Test
    @DisplayName("Soporta cruce de medianoche (18:00-02:00): permanece abierto a 23:30, avisa antes de 02:00 y cierra a 02:05")
    void handlesMidnightCrossoverProperly() {
        UUID tenantId = UUID.randomUUID();
        Tenant nightBar = Tenant.builder()
                .id(tenantId)
                .name("Night Burger")
                .slug("night-burger")
                .isActive(true)
                .openingTime(LocalTime.of(18, 0))
                .closingTime(LocalTime.of(2, 0))
                .isOpen(true)
                .build();

        when(tenantRepository.findAllByIsActiveTrueAndOpeningTimeIsNotNullAndClosingTimeIsNotNull())
                .thenReturn(List.of(nightBar));

        LocalDate today = LocalDate.of(2026, 11, 22);

        // 23:30 debe seguir abierto sin cambios ni avisos
        int updatedAtNight = scheduler.processStoreSchedules(LocalTime.of(23, 30), today);
        assertEquals(0, updatedAtNight);
        assertTrue(nightBar.getIsOpen());

        // 01:50 debe seguir abierto y enviar aviso STORE_CLOSING_SOON (faltan 10 min)
        int updatedBeforeClose = scheduler.processStoreSchedules(LocalTime.of(1, 50), today);
        assertEquals(0, updatedBeforeClose);
        verify(notificationService).notifyTenantStaff(
                eq(nightBar),
                eq(List.of(TenantStaffRole.OWNER, TenantStaffRole.ADMIN, TenantStaffRole.COCINERO)),
                eq(NotificationType.STORE_CLOSING_SOON),
                anyString(),
                anyString(),
                eq(tenantId),
                eq("STORE_CLOSING_SOON:" + tenantId + ":" + today + ":02:00"),
                anyMap()
        );

        // 02:05 debe cerrar automáticamente
        int updatedAfterClose = scheduler.processStoreSchedules(LocalTime.of(2, 5), today);
        assertEquals(1, updatedAfterClose);
        assertFalse(nightBar.getIsOpen());
        verify(notificationService).notifyTenantStaff(
                eq(nightBar),
                eq(List.of(TenantStaffRole.OWNER, TenantStaffRole.ADMIN)),
                eq(NotificationType.STORE_AUTO_CLOSED),
                anyString(),
                anyString(),
                eq(tenantId),
                eq("STORE_AUTO_CLOSED:" + tenantId + ":" + today + ":02:00"),
                anyMap()
        );
    }
}
