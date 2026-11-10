package lat.nexofood.api.modules.store.application.service.tenant;

import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.store.application.usecase.tenant.GetTenantBySlugUseCase;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetTenantBySlugService implements GetTenantBySlugUseCase {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;

    @Override
    @Transactional(readOnly = true)
    public TenantResponse execute(String slug) {
        return tenantRepository.findBySlug(slug)
                .map(tenantMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado con slug: " + slug));
    }
}
