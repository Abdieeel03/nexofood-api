package lat.nexofood.api.modules.catalog.web.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record ProductAvailabilityRequest(
        @NotNull(message = "El estado de disponibilidad (isAvailable) es obligatorio")
        Boolean isAvailable
) {}
