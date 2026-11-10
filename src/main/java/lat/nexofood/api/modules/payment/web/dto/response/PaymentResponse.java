package lat.nexofood.api.modules.payment.web.dto.response;

import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record PaymentResponse(
        UUID id,
        UUID orderId,
        String orderNumber,
        UUID tenantId,
        UUID customerId,
        String mpOrderId,
        String mpPaymentId,
        String paymentMethod,
        PaymentStatus status,
        String statusDetail,
        BigDecimal amount,
        String currency,
        OffsetDateTime expiresAt,
        OffsetDateTime approvedAt,
        OffsetDateTime refundedAt,
        String refundReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
