package lat.nexofood.api.modules.store.application.service.customer;

import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.store.application.usecase.customer.AdjustLoyaltyPointsUseCase;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantCustomerRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantCustomerResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantCustomerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdjustLoyaltyPointsService implements AdjustLoyaltyPointsUseCase {

    private final TenantCustomerRepository tenantCustomerRepository;
    private final TenantCustomerMapper tenantCustomerMapper;

    @Override
    @Transactional
    public TenantCustomerResponse execute(UUID tenantId, UUID customerId, int pointsAdjustment) {
        TenantCustomer customer = tenantCustomerRepository.findById(customerId)
                .filter(tc -> tc.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado en este restaurante"));

        int newPoints = customer.getLoyaltyPoints() + pointsAdjustment;
        customer.setLoyaltyPoints(Math.max(0, newPoints)); // no permitir puntos negativos
        TenantCustomer updated = tenantCustomerRepository.save(customer);
        log.info("Puntos de cliente {} ajustados en {} → total: {}", customerId, pointsAdjustment, updated.getLoyaltyPoints());
        return tenantCustomerMapper.toResponse(updated);
    }
}
