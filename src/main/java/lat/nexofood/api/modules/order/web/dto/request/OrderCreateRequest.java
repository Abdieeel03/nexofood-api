package lat.nexofood.api.modules.order.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

        @Size(max = 255, message = "La dirección de entrega no puede superar los 255 caracteres")
        String deliveryAddress,

        @DecimalMin(value = "-90.0", message = "La latitud debe ser mayor o igual a -90")
        @DecimalMax(value = "90.0", message = "La latitud debe ser menor o igual a 90")
        Double deliveryLatitude,

        @DecimalMin(value = "-180.0", message = "La longitud debe ser mayor o igual a -180")
        @DecimalMax(value = "180.0", message = "La longitud debe ser menor o igual a 180")
        Double deliveryLongitude,

        @Size(max = 1000, message = "Las notas del pedido no pueden superar los 1000 caracteres")
        String notes,

        @NotEmpty(message = "La orden debe contener al menos un item")
        @Valid
        List<OrderItemRequest> items
) {}
