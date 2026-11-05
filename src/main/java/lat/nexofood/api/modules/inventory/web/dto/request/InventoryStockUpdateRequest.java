package lat.nexofood.api.modules.inventory.web.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record InventoryStockUpdateRequest(
        @DecimalMin(value = "0.000", message = "El stock mínimo no puede ser negativo")
        BigDecimal minimumStock,

        @DecimalMin(value = "0.000", message = "El stock máximo no puede ser negativo")
        BigDecimal maximumStock,

        @Size(max = 150, message = "La ubicación no puede exceder los 150 caracteres")
        String location,

        Boolean isActive
) {
}
