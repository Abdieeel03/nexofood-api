package lat.nexofood.api.modules.subscription.application.service.subscription;

import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionResponse;

import java.util.UUID;

public interface SubscriptionService {

    SubscriptionResponse startTrial(UUID planId);

    SubscriptionResponse getMySubscription();

    SubscriptionResponse cancel();
}
