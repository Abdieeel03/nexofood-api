package lat.nexofood.api.modules.order.web.dto.response;

import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record KitchenOrderResponse(
        UUID id,
        String orderNumber,
        DeliveryType deliveryType,
        OrderStatus status,
        String customerFirstName,
        String notes,
        List<KitchenOrderItemResponse> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
