package lat.nexofood.api.modules.subscription.mapper;

import lat.nexofood.api.modules.subscription.domain.SubscriptionPlan;
import lat.nexofood.api.modules.subscription.dto.request.SubscriptionPlanRequest;
import lat.nexofood.api.modules.subscription.dto.response.SubscriptionPlanResponse;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionPlanMapper {

    public SubscriptionPlanResponse toResponse(SubscriptionPlan plan) {
        if (plan == null) {
            return null;
        }
        return SubscriptionPlanResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .price(plan.getPrice())
                .billingCycleDays(plan.getBillingCycleDays())
                .maxProducts(plan.getMaxProducts())
                .isActive(plan.getIsActive())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }

    public SubscriptionPlan toEntity(SubscriptionPlanRequest request) {
        if (request == null) {
            return null;
        }
        return SubscriptionPlan.builder()
                .name(request.name())
                .price(request.price())
                .billingCycleDays(request.billingCycleDays() != null ? request.billingCycleDays() : 30)
                .maxProducts(request.maxProducts())
                .isActive(request.isActive() != null ? request.isActive() : true)
                .build();
    }
}
