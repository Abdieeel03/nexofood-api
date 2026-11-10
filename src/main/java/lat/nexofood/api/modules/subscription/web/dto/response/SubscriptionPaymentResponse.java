package lat.nexofood.api.modules.subscription.web.dto.response;

import lat.nexofood.api.modules.subscription.domain.SubscriptionPaymentStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record SubscriptionPaymentResponse(
        UUID id,
        UUID subscriptionId,
        String mpAuthorizedPaymentId,
        String mpPaymentId,
        SubscriptionPaymentStatus status,
        BigDecimal amount,
        String currency,
        OffsetDateTime paidAt,
        OffsetDateTime periodEnd,
        OffsetDateTime createdAt
) {}
