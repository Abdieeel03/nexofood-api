package lat.nexofood.api.modules.catalog.web.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record TaxStatusRequest(
        @NotNull(message = "El estado (isActive) es obligatorio")
        Boolean isActive
) {}
