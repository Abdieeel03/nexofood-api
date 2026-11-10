package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.modules.catalog.web.dto.request.TaxRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.TaxResponse;

import java.util.List;
import java.util.UUID;

public interface TaxService {

    List<TaxResponse> findAllByTenant(UUID tenantId);

    List<TaxResponse> findActiveByTenant(UUID tenantId);

    TaxResponse findByIdAndTenant(UUID id, UUID tenantId);

    TaxResponse create(UUID tenantId, TaxRequest request);

    TaxResponse update(UUID tenantId, UUID id, TaxRequest request);

    TaxResponse toggleStatus(UUID tenantId, UUID id, boolean isActive);

    void delete(UUID tenantId, UUID id);
}
