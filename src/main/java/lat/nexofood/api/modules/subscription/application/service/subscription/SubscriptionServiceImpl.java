package lat.nexofood.api.modules.subscription.application.service.subscription;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.subscription.domain.Subscription;
import lat.nexofood.api.modules.subscription.domain.SubscriptionPlan;
import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import lat.nexofood.api.modules.subscription.infrastructure.repository.SubscriptionPlanRepository;
import lat.nexofood.api.modules.subscription.infrastructure.repository.SubscriptionRepository;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionResponse;
import lat.nexofood.api.modules.subscription.web.mapper.SubscriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final List<SubscriptionStatus> ACTIVE_STATUSES = List.of(
            SubscriptionStatus.TRIAL,
            SubscriptionStatus.ACTIVE
    );

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final TenantRepository tenantRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SubscriptionMapper subscriptionMapper;

    @Override
    @Transactional
    public SubscriptionResponse startTrial(UUID planId) {
        User user = currentUserProvider.getCurrentUser();

        if (subscriptionRepository.existsByUserIdAndStatusIn(user.getId(), ACTIVE_STATUSES)) {
            throw new ResourceConflictException("El usuario ya cuenta con una suscripción vigente");
        }

        SubscriptionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan de suscripción no encontrado"));

        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new BadRequestException("El plan de suscripción seleccionado no está disponible");
        }

        OffsetDateTime startDate = OffsetDateTime.now();
        int days = plan.getBillingCycleDays() != null ? plan.getBillingCycleDays() : 30;
        OffsetDateTime endDate = startDate.plusDays(days);

        Subscription subscription = subscriptionMapper.toEntity(
                plan,
                user,
                SubscriptionStatus.TRIAL,
                startDate,
                endDate
        );

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Suscripción TRIAL iniciada con ID: {} para el usuario {}", saved.getId(), user.getId());
        return subscriptionMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse getMySubscription() {
        User user = currentUserProvider.getCurrentUser();

        Subscription subscription = subscriptionRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(user.getId(), ACTIVE_STATUSES)
                .or(() -> subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró ninguna suscripción para el usuario actual"));

        return subscriptionMapper.toResponse(subscription);
    }

    @Override
    @Transactional
    public SubscriptionResponse cancel() {
        User user = currentUserProvider.getCurrentUser();

        Subscription subscription = subscriptionRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(user.getId(), ACTIVE_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException("No tienes una suscripción activa para cancelar"));

        subscription.setStatus(SubscriptionStatus.CANCELED);
        Subscription saved = subscriptionRepository.save(subscription);

        tenantRepository.findBySubscriptionId(saved.getId()).ifPresent(tenant -> {
            tenant.setIsActive(false);
            tenantRepository.save(tenant);
            log.info("Restaurante {} desactivado por cancelación de suscripción {}", tenant.getId(), saved.getId());
        });

        log.info("Suscripción {} cancelada por el usuario {}", saved.getId(), user.getId());
        return subscriptionMapper.toResponse(saved);
    }
}
