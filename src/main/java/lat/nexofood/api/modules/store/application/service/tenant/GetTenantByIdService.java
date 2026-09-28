package lat.nexofood.api.modules.store.application.service.tenant;

import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.store.application.usecase.tenant.GetTenantByIdUseCase;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetTenantByIdService implements GetTenantByIdUseCase {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;

    @Override
    @Transactional(readOnly = true)
    public TenantResponse execute(UUID id) {
        return tenantRepository.findById(id)
                .map(tenantMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));
    }
}
