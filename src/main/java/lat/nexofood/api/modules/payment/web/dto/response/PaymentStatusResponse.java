package lat.nexofood.api.modules.payment.web.dto.response;

import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record PaymentStatusResponse(
        UUID paymentId,
        PaymentStatus status,
        String statusDetail,
        UUID orderId,
        String orderNumber,
        BigDecimal amount,
        String currency,
        OffsetDateTime approvedAt,
        OffsetDateTime refundedAt
) {}
