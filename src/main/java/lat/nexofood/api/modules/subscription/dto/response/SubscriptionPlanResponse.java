package lat.nexofood.api.modules.subscription.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record SubscriptionPlanResponse(
        UUID id,
        String name,
        BigDecimal price,
        Integer billingCycleDays,
        Integer maxProducts,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
