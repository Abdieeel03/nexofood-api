package lat.nexofood.api.modules.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record PaymentCreateRequest(
        @NotNull(message = "El ID de la orden es obligatorio")
        UUID orderId,

        @NotNull(message = "El ID del tenant es obligatorio")
        UUID tenantId,

        @Size(max = 100)
        String mpPaymentId,

        @Size(max = 100)
        String mpPreferenceId,

        @Size(max = 50)
        String paymentMethod,

        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
        BigDecimal amount,

        String rawResponse
) {}
