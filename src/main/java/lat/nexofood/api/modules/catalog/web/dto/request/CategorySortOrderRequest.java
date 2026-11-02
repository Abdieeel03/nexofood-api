package lat.nexofood.api.modules.catalog.web.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record CategorySortOrderRequest(
        @NotNull(message = "El orden (sortOrder) es obligatorio")
        @Min(value = 0, message = "El orden debe ser igual o mayor a 0")
        Integer sortOrder
) {}
