package lat.nexofood.api.modules.store.web.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CustomerNotesRequest(
        @Size(max = 2000, message = "Las notas no pueden exceder los 2000 caracteres")
        String notes
) {}
