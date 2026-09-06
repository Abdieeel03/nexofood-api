package lat.nexofood.api.modules.cart.web.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record CartItemResponse(
        UUID id,
        UUID productId,
        String productName,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal subtotal,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
