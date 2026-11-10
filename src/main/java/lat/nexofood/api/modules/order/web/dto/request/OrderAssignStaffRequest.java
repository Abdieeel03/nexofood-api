package lat.nexofood.api.modules.order.web.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record OrderAssignStaffRequest(
        @NotNull(message = "El ID del repartidor es obligatorio")
        UUID deliveryStaffId
) {}
