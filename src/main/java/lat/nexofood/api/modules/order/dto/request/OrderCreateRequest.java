package lat.nexofood.api.modules.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record OrderCreateRequest(
        @NotNull(message = "El ID del tenant es obligatorio")
        UUID tenantId,

        @NotNull(message = "El tipo de entrega es obligatorio")
        DeliveryType deliveryType,

        UUID customerAddressId,
        String deliveryAddress,
        Double deliveryLatitude,
        Double deliveryLongitude,

        String notes,

        @NotEmpty(message = "La orden debe contener al menos un item")
        @Valid
        List<OrderItemRequest> items
) {}
