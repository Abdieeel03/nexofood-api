package lat.nexofood.api.modules.order.web.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record KitchenOrderItemResponse(
        UUID id,
        UUID productId,
        String productName,
        Integer quantity,
        String notes
) {}
