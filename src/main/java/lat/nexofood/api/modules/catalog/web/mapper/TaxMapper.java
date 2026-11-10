package lat.nexofood.api.modules.catalog.web.mapper;

import lat.nexofood.api.modules.catalog.domain.Tax;
import lat.nexofood.api.modules.catalog.web.dto.request.TaxRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.TaxResponse;
import lat.nexofood.api.modules.store.domain.Tenant;
import org.springframework.stereotype.Component;

@Component
public class TaxMapper {

    public TaxResponse toResponse(Tax tax) {
        if (tax == null) {
            return null;
        }

        return TaxResponse.builder()
                .id(tax.getId())
                .tenantId(tax.getTenant() != null ? tax.getTenant().getId() : null)
                .name(tax.getName())
                .rate(tax.getRate())
                .code(tax.getCode())
                .isInclusive(tax.getIsInclusive())
                .isActive(tax.getIsActive())
                .createdAt(tax.getCreatedAt())
                .updatedAt(tax.getUpdatedAt())
                .build();
    }

    public Tax toEntity(TaxRequest request, Tenant tenant) {
        if (request == null) {
            return null;
        }

        return Tax.builder()
                .tenant(tenant)
                .name(request.name())
                .rate(request.rate())
                .code(request.code())
                .isInclusive(request.isInclusive() != null ? request.isInclusive() : true)
                .isActive(request.isActive() != null ? request.isActive() : true)
                .build();
    }
}
