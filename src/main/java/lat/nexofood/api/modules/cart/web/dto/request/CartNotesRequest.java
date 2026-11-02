package lat.nexofood.api.modules.cart.web.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CartNotesRequest(
        @Size(max = 1000, message = "Las notas del carrito no pueden superar los 1000 caracteres")
        String notes
) {}
