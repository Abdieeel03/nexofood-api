package lat.nexofood.api.modules.store.application.service.tenant;

import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.store.application.usecase.tenant.ChangeTenantStatusUseCase;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeTenantStatusService implements ChangeTenantStatusUseCase {

    private final TenantRepository tenantRepository;

    @Override
    @Transactional
    public void execute(UUID id, boolean isActive) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));
        tenant.setIsActive(isActive);
        tenantRepository.save(tenant);
        log.info("Estado del tenant {} cambiado a: {}", id, isActive);
    }
}
