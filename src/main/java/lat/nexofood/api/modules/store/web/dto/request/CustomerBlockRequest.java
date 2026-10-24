package lat.nexofood.api.modules.store.web.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record CustomerBlockRequest(
        @NotNull(message = "El campo 'isBlocked' es obligatorio")
        Boolean isBlocked
) {}
