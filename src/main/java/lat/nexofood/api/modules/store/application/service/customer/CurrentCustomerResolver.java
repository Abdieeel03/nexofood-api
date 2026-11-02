package lat.nexofood.api.modules.store.application.service.customer;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ForbiddenException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.domain.TenantCustomerId;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantCustomerRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentCustomerResolver {

    private final CurrentUserProvider currentUserProvider;
    private final TenantRepository tenantRepository;
    private final TenantCustomerRepository tenantCustomerRepository;

    @Transactional
    public TenantCustomer resolveBySlug(String slug) {
        User user = currentUserProvider.getCurrentUser();
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado con slug: " + slug));

        validateTenantActive(tenant);
        return resolveForTenantAndUser(tenant, user);
    }

    @Transactional
    public TenantCustomer resolveByTenantId(UUID tenantId) {
        User user = currentUserProvider.getCurrentUser();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado con ID: " + tenantId));

        validateTenantActive(tenant);
        return resolveForTenantAndUser(tenant, user);
    }

    @Transactional
    public TenantCustomer resolveForTenantAndUser(Tenant tenant, User user) {
        validateTenantActive(tenant);

        TenantCustomerId customerId = new TenantCustomerId(tenant.getId(), user.getId());
        TenantCustomer customer = tenantCustomerRepository.findById(customerId)
                .orElseGet(() -> {
                    log.info("Autoasociando cliente userId={} a la tienda '{}' ({})",
                            user.getId(), tenant.getSlug(), tenant.getId());
                    TenantCustomer newCustomer = TenantCustomer.builder()
                            .id(customerId)
                            .tenant(tenant)
                            .user(user)
                            .loyaltyPoints(0)
                            .isBlocked(false)
                            .totalOrders(0)
                            .build();
                    return tenantCustomerRepository.save(newCustomer);
                });

        if (Boolean.TRUE.equals(customer.getIsBlocked())) {
            throw new ForbiddenException("Tu cuenta ha sido bloqueada por este comercio. " +
                    "Por favor contacta al restaurante para más información.");
        }

        return customer;
    }

    private void validateTenantActive(Tenant tenant) {
        if (!Boolean.TRUE.equals(tenant.getIsActive())) {
            throw new BadRequestException("El restaurante '" + tenant.getName() + "' no se encuentra activo actualmente");
        }
    }
}
