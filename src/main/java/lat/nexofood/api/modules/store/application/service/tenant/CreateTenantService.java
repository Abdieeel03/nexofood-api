package lat.nexofood.api.modules.store.application.service.tenant;

import lat.nexofood.api.common.exception.ForbiddenException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.subscription.domain.Subscription;
import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import lat.nexofood.api.modules.subscription.infrastructure.repository.SubscriptionRepository;
import lat.nexofood.api.modules.store.application.usecase.tenant.CreateTenantUseCase;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantMember;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.request.TenantCreateRequest;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateTenantService implements CreateTenantUseCase {

    private static final List<SubscriptionStatus> VALID_STATUSES = List.of(
            SubscriptionStatus.TRIAL,
            SubscriptionStatus.ACTIVE
    );

    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final TenantMapper tenantMapper;

    @Override
    @Transactional
    public TenantResponse execute(TenantCreateRequest request) {
        log.info("Creando tenant con slug: {}", request.slug());

        User owner = currentUserProvider.getCurrentUser();

        Subscription subscription;
        if (request.subscriptionId() != null) {
            subscription = subscriptionRepository.findById(request.subscriptionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));

            if (subscription.getUser() == null || !subscription.getUser().getId().equals(owner.getId())) {
                throw new ForbiddenException("La suscripción especificada no pertenece al usuario autenticado");
            }
        } else {
            subscription = subscriptionRepository
                    .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(owner.getId(), VALID_STATUSES)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No cuentas con una suscripción vigente para crear un restaurante"));
        }

        if (!VALID_STATUSES.contains(subscription.getStatus())
                || (subscription.getEndDate() != null && subscription.getEndDate().isBefore(OffsetDateTime.now()))) {
            throw new ResourceConflictException("La suscripción no está activa o se encuentra vencida");
        }

        if (tenantRepository.findBySubscriptionId(subscription.getId()).isPresent()) {
            throw new ResourceConflictException("La suscripción ya se encuentra vinculada a un restaurante");
        }

        if (tenantRepository.existsBySlug(request.slug())) {
            throw new ResourceConflictException("El slug ya está en uso por otro restaurante");
        }

        if (tenantRepository.existsByOwnerId(owner.getId())) {
            throw new ResourceConflictException("El usuario ya es propietario de un restaurante");
        }

        Tenant tenant = tenantMapper.toEntity(request, subscription, owner);
        Tenant savedTenant = tenantRepository.save(tenant);

        // Crear automáticamente el TenantMember con rol OWNER
        TenantMember ownerMember = TenantMember.builder()
                .tenant(savedTenant)
                .user(owner)
                .role(TenantStaffRole.OWNER)
                .isActive(true)
                .build();
        tenantMemberRepository.save(ownerMember);

        log.info("Tenant creado exitosamente con ID: {}", savedTenant.getId());
        return tenantMapper.toResponse(savedTenant);
    }
}
