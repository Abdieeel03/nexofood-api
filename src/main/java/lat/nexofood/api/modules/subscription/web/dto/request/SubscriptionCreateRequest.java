package lat.nexofood.api.modules.subscription.web.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record SubscriptionCreateRequest(
        @NotNull(message = "El ID del plan es obligatorio")
        UUID planId
) {}
