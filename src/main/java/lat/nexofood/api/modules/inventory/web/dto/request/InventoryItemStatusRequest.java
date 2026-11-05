package lat.nexofood.api.modules.inventory.web.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record InventoryItemStatusRequest(
        @NotNull(message = "El estado activo (isActive) es obligatorio")
        Boolean isActive
) {
}
