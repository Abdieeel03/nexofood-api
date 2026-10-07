package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.catalog.domain.Tax;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.catalog.infrastructure.repository.TaxRepository;
import lat.nexofood.api.modules.catalog.web.dto.request.TaxRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.TaxResponse;
import lat.nexofood.api.modules.catalog.web.mapper.TaxMapper;
import lat.nexofood.api.modules.order.infrastructure.repository.OrderItemRepository;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaxServiceImpl implements TaxService {

    private final TaxRepository taxRepository;
    private final TenantRepository tenantRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final TaxMapper taxMapper;

    @Override
    @Transactional(readOnly = true)
    public List<TaxResponse> findAllByTenant(UUID tenantId) {
        return taxRepository.findAllByTenantId(tenantId)
                .stream()
                .map(taxMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxResponse> findActiveByTenant(UUID tenantId) {
        return taxRepository.findAllByTenantIdAndIsActiveTrue(tenantId)
                .stream()
                .map(taxMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TaxResponse findByIdAndTenant(UUID id, UUID tenantId) {
        Tax tax = taxRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Impuesto no encontrado para el tenant: " + tenantId));
        return taxMapper.toResponse(tax);
    }

    @Override
    @Transactional
    public TaxResponse create(UUID tenantId, TaxRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant no encontrado con ID: " + tenantId));

        Tax tax = taxMapper.toEntity(request, tenant);
        Tax savedTax = taxRepository.save(tax);
        return taxMapper.toResponse(savedTax);
    }

    @Override
    @Transactional
    public TaxResponse update(UUID tenantId, UUID id, TaxRequest request) {
        Tax tax = taxRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Impuesto no encontrado con ID: " + id));

        // Regla de negocio: la tasa no se puede modificar si el impuesto ya está en uso
        if (request.rate() != null && request.rate().compareTo(tax.getRate()) != 0) {
            boolean isUsed = productRepository.existsByTaxId(id) || orderItemRepository.existsByTaxId(id);
            if (isUsed) {
                throw new BadRequestException(
                        "No se puede modificar la tasa (rate) de un impuesto que ya está en uso. " +
                        "Cree una nueva versión/configuración de impuesto o desactive este."
                );
            }
            tax.setRate(request.rate());
        }

        if (request.name() != null) {
            tax.setName(request.name());
        }
        if (request.code() != null) {
            tax.setCode(request.code());
        }
        if (request.isInclusive() != null) {
            tax.setIsInclusive(request.isInclusive());
        }
        if (request.isActive() != null) {
            tax.setIsActive(request.isActive());
        }

        Tax updatedTax = taxRepository.save(tax);
        return taxMapper.toResponse(updatedTax);
    }

    @Override
    @Transactional
    public TaxResponse toggleStatus(UUID tenantId, UUID id, boolean isActive) {
        Tax tax = taxRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Impuesto no encontrado con ID: " + id));

        tax.setIsActive(isActive);
        Tax updatedTax = taxRepository.save(tax);
        return taxMapper.toResponse(updatedTax);
    }

    @Override
    @Transactional
    public void delete(UUID tenantId, UUID id) {
        Tax tax = taxRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Impuesto no encontrado con ID: " + id));

        boolean isUsed = productRepository.existsByTaxId(id) || orderItemRepository.existsByTaxId(id);
        if (isUsed) {
            throw new BadRequestException(
                    "No se puede eliminar un impuesto asignado a productos o ventas históricas. " +
                    "Debe desactivarlo en su lugar."
            );
        }

        taxRepository.delete(tax);
    }
}
