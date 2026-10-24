package lat.nexofood.api.modules.subscription.web.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record SubscriptionPlanStatusRequest(
        @NotNull(message = "El estado de activación es obligatorio")
        Boolean isActive
) {}
