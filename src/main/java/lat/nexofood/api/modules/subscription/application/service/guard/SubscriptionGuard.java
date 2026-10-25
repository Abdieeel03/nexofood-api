package lat.nexofood.api.modules.subscription.application.service.guard;

import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.subscription.domain.Subscription;
import lat.nexofood.api.modules.subscription.domain.SubscriptionPlan;
import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SubscriptionGuard {

    private static final List<SubscriptionStatus> VALID_STATUSES = List.of(
            SubscriptionStatus.TRIAL,
            SubscriptionStatus.ACTIVE
    );

    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public Subscription assertActive(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));

        Subscription subscription = tenant.getSubscription();
        if (subscription == null) {
            throw new ResourceConflictException("El restaurante no tiene una suscripción asociada");
        }

        boolean statusValid = VALID_STATUSES.contains(subscription.getStatus());
        boolean notExpired = subscription.getEndDate() == null || !subscription.getEndDate().isBefore(OffsetDateTime.now());

        if (!statusValid || !notExpired) {
            throw new ResourceConflictException("La suscripción del restaurante no se encuentra vigente");
        }

        return subscription;
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan getLimits(UUID tenantId) {
        Subscription subscription = assertActive(tenantId);
        SubscriptionPlan plan = subscription.getPlan();
        if (plan == null) {
            throw new ResourceNotFoundException("Plan de suscripción no encontrado para el restaurante");
        }
        return plan;
    }
}
