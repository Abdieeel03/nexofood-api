package lat.nexofood.api.modules.store.web.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record CustomerPointsAdjustmentRequest(
        @NotNull(message = "El campo 'adjustment' es obligatorio")
        Integer adjustment
) {}
