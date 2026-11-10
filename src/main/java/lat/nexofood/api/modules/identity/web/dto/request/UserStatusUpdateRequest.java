package lat.nexofood.api.modules.identity.web.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record UserStatusUpdateRequest(
        @NotNull(message = "El campo 'isActive' es obligatorio")
        Boolean isActive
) {}
