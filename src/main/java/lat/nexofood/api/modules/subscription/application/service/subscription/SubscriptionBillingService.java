package lat.nexofood.api.modules.subscription.application.service.subscription;

import lat.nexofood.api.modules.subscription.domain.Subscription;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionCheckoutResponse;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionPaymentResponse;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionResponse;

import java.util.List;
import java.util.UUID;

public interface SubscriptionBillingService {

    SubscriptionCheckoutResponse startCheckout(UUID planId);

    void processPreapprovalNotification(String preapprovalId);

    void processAuthorizedPaymentNotification(String authorizedPaymentId);

    void cancelPreapproval(Subscription subscription);

    List<SubscriptionPaymentResponse> getMyPayments();

    SubscriptionResponse syncMySubscription();
}
