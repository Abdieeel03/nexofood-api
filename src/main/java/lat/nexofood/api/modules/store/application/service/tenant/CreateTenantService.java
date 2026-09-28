package lat.nexofood.api.modules.store.application.service.tenant;

import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateTenantService implements CreateTenantUseCase {

    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantMapper tenantMapper;

    @Override
    @Transactional
    public TenantResponse execute(TenantCreateRequest request) {
        log.info("Creando tenant con slug: {}", request.slug());

        Subscription subscription = subscriptionRepository.findById(request.subscriptionId())
                .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE
                && subscription.getStatus() != SubscriptionStatus.TRIAL) {
            throw new ResourceConflictException("La suscripción no está activa");
        }

        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario propietario no encontrado"));

        if (tenantRepository.existsBySlug(request.slug())) {
            throw new ResourceConflictException("El slug ya está en uso por otro restaurante");
        }

        if (tenantRepository.existsByOwnerId(request.ownerId())) {
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
