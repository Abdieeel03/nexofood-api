package lat.nexofood.api.modules.catalog.web.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record TaxResponse(
        UUID id,
        UUID tenantId,
        String name,
        BigDecimal rate,
        String code,
        Boolean isInclusive,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
