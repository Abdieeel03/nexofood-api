package lat.nexofood.api.modules.subscription.application.service.plan;

import lat.nexofood.api.modules.subscription.web.dto.request.SubscriptionPlanRequest;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionPlanResponse;

import java.util.List;
import java.util.UUID;

public interface SubscriptionPlanService {

    List<SubscriptionPlanResponse> getActivePlans();

    List<SubscriptionPlanResponse> getAllPlans();

    SubscriptionPlanResponse getPlanById(UUID planId);

    SubscriptionPlanResponse createPlan(SubscriptionPlanRequest request);

    SubscriptionPlanResponse updatePlan(UUID planId, SubscriptionPlanRequest request);

    SubscriptionPlanResponse changeStatus(UUID planId, Boolean isActive);
}
