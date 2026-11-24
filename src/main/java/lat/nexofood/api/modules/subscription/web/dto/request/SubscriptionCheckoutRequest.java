package lat.nexofood.api.modules.subscription.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record SubscriptionCheckoutRequest(
        @NotNull(message = "El ID del plan es obligatorio")
        UUID planId,
        String cardTokenId,
        @Email(message = "El correo del pagador no es válido")
        String payerEmail
) {}
