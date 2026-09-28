package lat.nexofood.api.modules.store.application.service.customer;

import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.store.application.usecase.customer.GetTenantCustomersUseCase;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantCustomerRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantCustomerResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantCustomerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetTenantCustomersService implements GetTenantCustomersUseCase {

    private final TenantRepository tenantRepository;
    private final TenantCustomerRepository tenantCustomerRepository;
    private final TenantCustomerMapper tenantCustomerMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<TenantCustomerResponse> execute(UUID tenantId, Pageable pageable) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));
        return tenantCustomerRepository.findByTenantId(tenantId, pageable)
                .map(tenantCustomerMapper::toResponse);
    }
}
