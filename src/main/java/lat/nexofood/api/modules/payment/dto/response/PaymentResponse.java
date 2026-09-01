package lat.nexofood.api.modules.payment.dto.response;

import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record PaymentResponse(
        UUID id,
        UUID orderId,
        UUID tenantId,
        String mpPaymentId,
        String mpPreferenceId,
        String paymentMethod,
        PaymentStatus status,
        BigDecimal amount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
