package lat.nexofood.api.modules.catalog.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CategoryRequest(
        @NotBlank(message = "El nombre de la categoría es obligatorio")
        @Size(max = 100)
        String name,

        String description,

        @Min(value = 0, message = "El orden debe ser igual o mayor a 0")
        Integer sortOrder,

        Boolean isActive
) {}
