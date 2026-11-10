package lat.nexofood.api.modules.subscription.web.dto.response;

import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import lombok.Builder;

import java.util.UUID;

@Builder
public record SubscriptionCheckoutResponse(
        UUID subscriptionId,
        UUID planId,
        String mpPreapprovalId,
        String initPoint,
        SubscriptionStatus status
) {}
