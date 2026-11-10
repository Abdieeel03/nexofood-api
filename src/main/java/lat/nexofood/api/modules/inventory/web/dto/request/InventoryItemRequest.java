package lat.nexofood.api.modules.inventory.web.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lat.nexofood.api.modules.inventory.domain.InventoryUnit;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record InventoryItemRequest(
        @NotBlank(message = "El nombre del insumo es obligatorio")
        @Size(max = 150, message = "El nombre del insumo no puede exceder los 150 caracteres")
        String name,

        @Size(max = 50, message = "El código SKU no puede exceder los 50 caracteres")
        String sku,

        @Size(max = 1000, message = "La descripción no puede exceder los 1000 caracteres")
        String description,

        @Size(max = 100, message = "La categoría no puede exceder los 100 caracteres")
        String category,

        InventoryUnit unit,

        @DecimalMin(value = "0.00", message = "El costo unitario no puede ser negativo")
        @DecimalMax(value = "999999.99", message = "El costo unitario no puede exceder 999,999.99")
        BigDecimal costPrice,

        Boolean isActive
) {
}
