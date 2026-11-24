package lat.nexofood.api.modules.subscription.web.dto.response;

import lombok.Builder;

@Builder
public record SubscriptionPaymentConfigResponse(
        String publicKey,
        String currency,
        boolean cardPaymentsEnabled
) {}
