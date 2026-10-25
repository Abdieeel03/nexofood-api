package lat.nexofood.api.modules.subscription.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.subscription.application.scheduler.SubscriptionExpirationScheduler;
import lat.nexofood.api.modules.subscription.application.service.guard.SubscriptionGuard;
import lat.nexofood.api.modules.subscription.application.service.plan.SubscriptionPlanServiceImpl;
import lat.nexofood.api.modules.subscription.application.service.subscription.SubscriptionServiceImpl;
import lat.nexofood.api.modules.subscription.domain.Subscription;
import lat.nexofood.api.modules.subscription.domain.SubscriptionPlan;
import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import lat.nexofood.api.modules.subscription.infrastructure.repository.SubscriptionPlanRepository;
import lat.nexofood.api.modules.subscription.infrastructure.repository.SubscriptionRepository;
import lat.nexofood.api.modules.subscription.web.dto.request.SubscriptionPlanRequest;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionPlanResponse;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionResponse;
import lat.nexofood.api.modules.subscription.web.mapper.SubscriptionMapper;
import lat.nexofood.api.modules.subscription.web.mapper.SubscriptionPlanMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionModuleServicesTest {

    @Mock
    private SubscriptionPlanRepository planRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private SubscriptionPlanMapper planMapper;
    private SubscriptionMapper subscriptionMapper;
    private SubscriptionPlanServiceImpl planService;
    private SubscriptionServiceImpl subscriptionService;
    private SubscriptionGuard subscriptionGuard;
    private SubscriptionExpirationScheduler expirationScheduler;

    @BeforeEach
    void setUp() {
        planMapper = new SubscriptionPlanMapper();
        subscriptionMapper = new SubscriptionMapper(planMapper);
        planService = new SubscriptionPlanServiceImpl(planRepository, planMapper);
        subscriptionService = new SubscriptionServiceImpl(
                subscriptionRepository,
                planRepository,
                tenantRepository,
                currentUserProvider,
                subscriptionMapper
        );
        subscriptionGuard = new SubscriptionGuard(tenantRepository);
        expirationScheduler = new SubscriptionExpirationScheduler(subscriptionRepository, tenantRepository);
    }

    @Test
    @DisplayName("SubscriptionPlanService: crea un plan cuando el nombre es único")
    void createsPlanSuccessfully() {
        SubscriptionPlanRequest request = SubscriptionPlanRequest.builder()
                .name("Plan Pro")
                .price(new BigDecimal("99.90"))
                .billingCycleDays(30)
                .maxProducts(150)
                .isActive(true)
                .build();

        when(planRepository.existsByNameIgnoreCase("Plan Pro")).thenReturn(false);
        when(planRepository.save(any(SubscriptionPlan.class))).thenAnswer(inv -> {
            SubscriptionPlan saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        SubscriptionPlanResponse response = planService.createPlan(request);

        assertNotNull(response.id());
        assertEquals("Plan Pro", response.name());
        assertEquals(150, response.maxProducts());
        assertTrue(response.isActive());
    }

    @Test
    @DisplayName("SubscriptionPlanService: rechaza nombre duplicado con 409 Conflict")
    void rejectsDuplicatePlanName() {
        SubscriptionPlanRequest request = SubscriptionPlanRequest.builder()
                .name("Plan Básico")
                .price(new BigDecimal("49.90"))
                .billingCycleDays(30)
                .build();

        when(planRepository.existsByNameIgnoreCase("Plan Básico")).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> planService.createPlan(request));
        verify(planRepository, never()).save(any());
    }

    @Test
    @DisplayName("SubscriptionService.startTrial: inicia trial tomando usuario del token JWT")
    void startsTrialUsingAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        User user = User.builder().id(userId).email("chef@nexofood.lat").build();
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .id(planId)
                .name("Plan Emprendedor")
                .price(new BigDecimal("59.00"))
                .billingCycleDays(14)
                .maxProducts(50)
                .isActive(true)
                .build();

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(subscriptionRepository.existsByUserIdAndStatusIn(eq(userId), any())).thenReturn(false);
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        SubscriptionResponse response = subscriptionService.startTrial(planId);

        assertNotNull(response.id());
        assertEquals(userId, response.userId());
        assertEquals(SubscriptionStatus.TRIAL, response.status());
        assertEquals("Plan Emprendedor", response.plan().name());
        assertTrue(response.endDate().isAfter(response.startDate()));
    }

    @Test
    @DisplayName("SubscriptionService.startTrial: rechaza plan inactivo con BadRequestException")
    void rejectsInactivePlanOnStartTrial() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        User user = User.builder().id(userId).build();
        SubscriptionPlan inactivePlan = SubscriptionPlan.builder()
                .id(planId)
                .name("Plan Legacy")
                .isActive(false)
                .build();

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(subscriptionRepository.existsByUserIdAndStatusIn(eq(userId), any())).thenReturn(false);
        when(planRepository.findById(planId)).thenReturn(Optional.of(inactivePlan));

        assertThrows(BadRequestException.class, () -> subscriptionService.startTrial(planId));
    }

    @Test
    @DisplayName("SubscriptionService.cancel: cancela la suscripción y desactiva el tenant asociado")
    void cancelsSubscriptionAndDeactivatesLinkedTenant() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        User user = User.builder().id(userId).build();
        SubscriptionPlan plan = SubscriptionPlan.builder().id(UUID.randomUUID()).name("Pro").build();
        Subscription subscription = Subscription.builder()
                .id(subscriptionId)
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.TRIAL)
                .build();

        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .subscription(subscription)
                .isActive(true)
                .build();

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(subscriptionRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(subscription)).thenReturn(subscription);
        when(tenantRepository.findBySubscriptionId(subscriptionId)).thenReturn(Optional.of(tenant));

        SubscriptionResponse response = subscriptionService.cancel();

        assertEquals(SubscriptionStatus.CANCELED, response.status());
        assertFalse(tenant.getIsActive());
        verify(tenantRepository).save(tenant);
    }

    @Test
    @DisplayName("SubscriptionGuard: devuelve límites cuando la suscripción está vigente y lanza conflicto si venció")
    void subscriptionGuardValidatesAndReturnsLimits() {
        UUID tenantId = UUID.randomUUID();
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .id(UUID.randomUUID())
                .name("Plan Pro")
                .maxProducts(100)
                .build();

        Subscription activeSub = Subscription.builder()
                .id(UUID.randomUUID())
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .endDate(OffsetDateTime.now().plusDays(5))
                .build();

        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .subscription(activeSub)
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        SubscriptionPlan limits = subscriptionGuard.getLimits(tenantId);
        assertEquals(100, limits.getMaxProducts());

        // Si la suscripción ya venció
        activeSub.setEndDate(OffsetDateTime.now().minusMinutes(1));
        assertThrows(ResourceConflictException.class, () -> subscriptionGuard.assertActive(tenantId));
    }

    @Test
    @DisplayName("SubscriptionExpirationScheduler: pasa TRIAL a EXPIRED, ACTIVE a PAST_DUE y desactiva tenants")
    void schedulerExpiresSubscriptionsAndDeactivatesTenants() {
        Subscription expiredTrial = Subscription.builder()
                .id(UUID.randomUUID())
                .status(SubscriptionStatus.TRIAL)
                .endDate(OffsetDateTime.now().minusHours(2))
                .build();

        Subscription expiredActive = Subscription.builder()
                .id(UUID.randomUUID())
                .status(SubscriptionStatus.ACTIVE)
                .endDate(OffsetDateTime.now().minusDays(1))
                .build();

        Tenant tenantTrial = Tenant.builder()
                .id(UUID.randomUUID())
                .subscription(expiredTrial)
                .isActive(true)
                .build();

        Tenant tenantActive = Tenant.builder()
                .id(UUID.randomUUID())
                .subscription(expiredActive)
                .isActive(true)
                .build();

        when(subscriptionRepository.findAllByStatusInAndEndDateBefore(any(), any()))
                .thenReturn(List.of(expiredTrial, expiredActive));
        when(tenantRepository.findBySubscriptionId(expiredTrial.getId()))
                .thenReturn(Optional.of(tenantTrial));
        when(tenantRepository.findBySubscriptionId(expiredActive.getId()))
                .thenReturn(Optional.of(tenantActive));

        int processed = expirationScheduler.processExpiredSubscriptions();

        assertEquals(2, processed);
        assertEquals(SubscriptionStatus.EXPIRED, expiredTrial.getStatus());
        assertEquals(SubscriptionStatus.PAST_DUE, expiredActive.getStatus());
        assertFalse(tenantTrial.getIsActive());
        assertFalse(tenantActive.getIsActive());
        verify(tenantRepository, times(2)).save(any(Tenant.class));
    }
}
