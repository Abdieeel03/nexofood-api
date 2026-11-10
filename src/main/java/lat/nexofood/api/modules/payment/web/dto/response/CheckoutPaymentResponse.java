package lat.nexofood.api.modules.payment.web.dto.response;

import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record CheckoutPaymentResponse(
        UUID paymentId,
        String mpOrderId,
        String checkoutUrl,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        OffsetDateTime expiresAt
) {}
