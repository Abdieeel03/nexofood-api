package lat.nexofood.api.modules.subscription.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record SubscriptionCreateRequest(
        @NotNull(message = "El ID del plan es obligatorio")
        UUID planId,

        @NotNull(message = "El ID del usuario es obligatorio")
        UUID userId
) {}
