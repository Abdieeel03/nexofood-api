package lat.nexofood.api.modules.order.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lombok.Builder;

import java.util.UUID;

@Builder
public record OrderCheckoutRequest(
        @NotNull(message = "El tipo de entrega es obligatorio")
        DeliveryType deliveryType,

        UUID customerAddressId,
        String deliveryAddress,
        Double deliveryLatitude,
        Double deliveryLongitude,

        @Size(max = 20, message = "El RUC no puede superar los 20 caracteres")
        String ruc,

        @Size(max = 1000, message = "Las notas del pedido no pueden superar los 1000 caracteres")
        String notes
) {}
