package lat.nexofood.api.modules.subscription.mapper;

import lat.nexofood.api.modules.auth.domain.User;
import lat.nexofood.api.modules.subscription.domain.Subscription;
import lat.nexofood.api.modules.subscription.domain.SubscriptionPlan;
import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import lat.nexofood.api.modules.subscription.dto.response.SubscriptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class SubscriptionMapper {

    private final SubscriptionPlanMapper planMapper;

    public SubscriptionResponse toResponse(Subscription subscription) {
        if (subscription == null) {
            return null;
        }
        return SubscriptionResponse.builder()
                .id(subscription.getId())
                .plan(planMapper.toResponse(subscription.getPlan()))
                .userId(subscription.getUser() != null ? subscription.getUser().getId() : null)
                .status(subscription.getStatus())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .mpPreapprovalId(subscription.getMpPreapprovalId())
                .createdAt(subscription.getCreatedAt())
                .updatedAt(subscription.getUpdatedAt())
                .build();
    }

    public Subscription toEntity(SubscriptionPlan plan, User user, SubscriptionStatus status, OffsetDateTime startDate, OffsetDateTime endDate) {
        return Subscription.builder()
                .plan(plan)
                .user(user)
                .status(status != null ? status : SubscriptionStatus.TRIAL)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }
}
