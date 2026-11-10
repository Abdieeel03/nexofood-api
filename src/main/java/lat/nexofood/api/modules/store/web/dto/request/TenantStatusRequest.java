package lat.nexofood.api.modules.store.web.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record TenantStatusRequest(
        @NotNull(message = "El campo 'isActive' es obligatorio")
        Boolean isActive
) {}
