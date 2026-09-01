package lat.nexofood.api.modules.subscription.dto.response;

import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record SubscriptionResponse(
        UUID id,
        SubscriptionPlanResponse plan,
        UUID userId,
        SubscriptionStatus status,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        String mpPreapprovalId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
