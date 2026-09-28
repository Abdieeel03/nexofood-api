package lat.nexofood.api.modules.store.application.service.customer;

import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.store.application.usecase.customer.UpdateTenantCustomerNotesUseCase;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantCustomerRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantCustomerResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantCustomerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateTenantCustomerNotesService implements UpdateTenantCustomerNotesUseCase {

    private final TenantCustomerRepository tenantCustomerRepository;
    private final TenantCustomerMapper tenantCustomerMapper;

    @Override
    @Transactional
    public TenantCustomerResponse execute(UUID tenantId, UUID customerId, String notes) {
        TenantCustomer customer = tenantCustomerRepository.findById(customerId)
                .filter(tc -> tc.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado en este restaurante"));

        customer.setNotes(notes);
        TenantCustomer updated = tenantCustomerRepository.save(customer);
        return tenantCustomerMapper.toResponse(updated);
    }
}
