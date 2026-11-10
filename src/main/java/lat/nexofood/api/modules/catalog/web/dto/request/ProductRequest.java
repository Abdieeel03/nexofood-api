package lat.nexofood.api.modules.catalog.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record ProductRequest(
        UUID categoryId,
        UUID taxId,

        @NotBlank(message = "El nombre del producto es obligatorio")
        @Size(max = 150)
        String name,

        @Size(max = 1000, message = "La descripción no puede exceder los 1000 caracteres")
        String description,

        @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
        @DecimalMax(value = "999999.99", message = "El precio no puede exceder 999,999.99")
        BigDecimal price,

        @Size(max = 500, message = "La URL de la imagen no puede exceder los 500 caracteres")
        String imageUrl,

        Boolean isAvailable,

        @Valid
        List<ProductPriceRequest> prices
) {}
