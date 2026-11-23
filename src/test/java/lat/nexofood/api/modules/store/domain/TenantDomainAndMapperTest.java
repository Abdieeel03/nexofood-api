package lat.nexofood.api.modules.store.domain;

import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.store.application.service.tenant.UpdateTenantService;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.request.TenantCreateRequest;
import lat.nexofood.api.modules.store.web.dto.request.TenantUpdateRequest;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMapper;
import lat.nexofood.api.modules.subscription.domain.Subscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantDomainAndMapperTest {

    @Mock
    private TenantRepository tenantRepository;

    private final TenantMapper tenantMapper = new TenantMapper();

    @Test
    @DisplayName("isWithinOperatingHours maneja horario diurno, bordes, 24h, nocturno e inactivo")
    void evaluatesOperatingHoursCorrectly() {
        Tenant dayStore = Tenant.builder()
                .isActive(true)
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(22, 0))
                .build();

        assertTrue(dayStore.isWithinOperatingHours(LocalTime.of(9, 0)));
        assertTrue(dayStore.isWithinOperatingHours(LocalTime.of(15, 30)));
        assertFalse(dayStore.isWithinOperatingHours(LocalTime.of(22, 0)));
        assertFalse(dayStore.isWithinOperatingHours(LocalTime.of(8, 59)));

        Tenant overnightStore = Tenant.builder()
                .isActive(true)
                .openingTime(LocalTime.of(18, 0))
                .closingTime(LocalTime.of(2, 0))
                .build();

        assertTrue(overnightStore.isWithinOperatingHours(LocalTime.of(18, 0)));
        assertTrue(overnightStore.isWithinOperatingHours(LocalTime.of(23, 59)));
        assertTrue(overnightStore.isWithinOperatingHours(LocalTime.of(1, 59)));
        assertFalse(overnightStore.isWithinOperatingHours(LocalTime.of(2, 0)));
        assertFalse(overnightStore.isWithinOperatingHours(LocalTime.of(12, 0)));

        Tenant alwaysOpen = Tenant.builder()
                .isActive(true)
                .openingTime(LocalTime.of(0, 0))
                .closingTime(LocalTime.of(0, 0))
                .build();
        assertTrue(alwaysOpen.isWithinOperatingHours(LocalTime.of(4, 0)));

        dayStore.setIsActive(false);
        assertFalse(dayStore.isWithinOperatingHours(LocalTime.of(15, 0)));
    }

    @Test
    @DisplayName("syncOpenStatusWithSchedule actualiza isOpen y retorna true solo cuando cambia")
    void syncOpenStatusWithScheduleTracksChanges() {
        Tenant tenant = Tenant.builder()
                .isActive(true)
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(22, 0))
                .isOpen(true)
                .build();

        assertFalse(tenant.syncOpenStatusWithSchedule(LocalTime.of(12, 0)));
        assertTrue(tenant.getIsOpen());

        assertTrue(tenant.syncOpenStatusWithSchedule(LocalTime.of(23, 0)));
        assertFalse(tenant.getIsOpen());

        assertFalse(tenant.syncOpenStatusWithSchedule(LocalTime.of(23, 30)));
        assertFalse(tenant.getIsOpen());
    }

    @Test
    @DisplayName("TenantMapper y UpdateTenantService mapean openingTime, closingTime e isOpen efectivo")
    void mapperAndUpdateServiceApplyScheduleFields() {
        User owner = User.builder().id(UUID.randomUUID()).build();
        Subscription subscription = Subscription.builder().id(UUID.randomUUID()).build();

        TenantCreateRequest createRequest = TenantCreateRequest.builder()
                .name("Chifa Nexo")
                .slug("chifa-nexo")
                .openingTime(LocalTime.of(10, 0))
                .closingTime(LocalTime.of(10, 0))
                .build();

        Tenant entity = tenantMapper.toEntity(createRequest, subscription, owner);
        assertEquals(LocalTime.of(10, 0), entity.getOpeningTime());
        assertEquals(LocalTime.of(10, 0), entity.getClosingTime());
        assertTrue(entity.getIsOpen());

        UUID tenantId = UUID.randomUUID();
        entity.setId(tenantId);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(entity));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTenantService updateTenantService = new UpdateTenantService(tenantRepository, tenantMapper);
        TenantUpdateRequest updateRequest = TenantUpdateRequest.builder()
                .name("Chifa Nexo Renovado")
                .openingTime(LocalTime.of(0, 0))
                .closingTime(LocalTime.of(0, 0))
                .build();

        TenantResponse updated = updateTenantService.execute(tenantId, updateRequest);
        assertEquals("Chifa Nexo Renovado", updated.name());
        assertEquals(LocalTime.of(0, 0), updated.openingTime());
        assertEquals(LocalTime.of(0, 0), updated.closingTime());
        assertTrue(updated.isOpen());
    }
}
