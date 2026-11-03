package lat.nexofood.api.modules.cart.web.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record CartResponse(
        UUID id,
        UUID tenantId,
        String tenantSlug,
        String tenantName,
        UUID customerId,
        UUID customerUserId,
        BigDecimal total,
        String notes,
        List<CartItemResponse> items,
        Boolean hasPriceChanges,
        Boolean hasUnavailableItems,
        List<String> warnings,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
