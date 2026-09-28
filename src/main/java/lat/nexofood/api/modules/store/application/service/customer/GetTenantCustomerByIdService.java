package lat.nexofood.api.modules.store.application.service.customer;

import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.store.application.usecase.customer.GetTenantCustomerByIdUseCase;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantCustomerRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantCustomerResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantCustomerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetTenantCustomerByIdService implements GetTenantCustomerByIdUseCase {

    private final TenantCustomerRepository tenantCustomerRepository;
    private final TenantCustomerMapper tenantCustomerMapper;

    @Override
    @Transactional(readOnly = true)
    public TenantCustomerResponse execute(UUID tenantId, UUID customerId) {
        return tenantCustomerRepository.findById(customerId)
                .filter(tc -> tc.getTenant().getId().equals(tenantId))
                .map(tenantCustomerMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado en este restaurante"));
    }
}
