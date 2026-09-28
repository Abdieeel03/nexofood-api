package lat.nexofood.api.modules.store.application.service.tenant;

import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.util.GeoUtils;
import lat.nexofood.api.modules.store.application.usecase.tenant.UpdateTenantUseCase;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.request.TenantUpdateRequest;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateTenantService implements UpdateTenantUseCase {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;

    @Override
    @Transactional
    public TenantResponse execute(UUID id, TenantUpdateRequest request) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));

        if (request.name() != null) tenant.setName(request.name());
        if (request.logoUrl() != null) tenant.setLogoUrl(request.logoUrl());
        if (request.bannerUrl() != null) tenant.setBannerUrl(request.bannerUrl());
        if (request.phone() != null) tenant.setPhone(request.phone());
        if (request.address() != null) tenant.setAddress(request.address());
        if (request.latitude() != null && request.longitude() != null) {
            tenant.setLocation(GeoUtils.createPoint(request.latitude(), request.longitude()));
        }
        if (request.deliveryRadiusKm() != null) tenant.setDeliveryRadiusKm(request.deliveryRadiusKm());
        if (request.defaultDeliveryFee() != null) tenant.setDefaultDeliveryFee(request.defaultDeliveryFee());
        if (request.isActive() != null) tenant.setIsActive(request.isActive());

        Tenant updated = tenantRepository.save(tenant);
        log.info("Tenant actualizado: {}", updated.getId());
        return tenantMapper.toResponse(updated);
    }
}
