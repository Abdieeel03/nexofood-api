package lat.nexofood.api.modules.order.web.dto.request;

import jakarta.validation.constraints.NotNull;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lombok.Builder;

@Builder
public record OrderStatusUpdateRequest(
        @NotNull(message = "El nuevo estado de la orden es obligatorio")
        OrderStatus status
) {}
