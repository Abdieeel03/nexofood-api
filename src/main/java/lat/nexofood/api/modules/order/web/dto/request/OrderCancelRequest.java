package lat.nexofood.api.modules.order.web.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record OrderCancelRequest(
        @Size(max = 500, message = "El motivo de cancelación no puede superar los 500 caracteres")
        String reason
) {}
