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
        UUID customerId,
        BigDecimal total,
        String notes,
        List<CartItemResponse> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
