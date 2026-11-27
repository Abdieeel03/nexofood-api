package lat.nexofood.api.modules.store.application.service.tenant;

import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.store.application.usecase.tenant.UpdateTenantStyleUseCase;
import lat.nexofood.api.modules.store.domain.StorefrontStyle;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateTenantStyleService implements UpdateTenantStyleUseCase {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;

    @Override
    @Caching(evict = {
            @CacheEvict(value = "storefront_menu", allEntries = true),
            @CacheEvict(value = "storefront_categories", allEntries = true)
    })
    @Transactional
    public TenantResponse execute(UUID id, StorefrontStyle storefrontStyle) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));

        tenant.setStorefrontStyle(storefrontStyle != null ? storefrontStyle : StorefrontStyle.MODERN);
        Tenant updated = tenantRepository.save(tenant);
        log.info("Estilo de storefront actualizado para tenant {}: {}", updated.getId(), updated.getStorefrontStyle());
        return tenantMapper.toResponse(updated);
    }
}
