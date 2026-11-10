package lat.nexofood.api.modules.payment.web.dto.response;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record MercadoPagoStatusResponse(
        UUID tenantId,
        boolean connected,
        String mpUserId,
        OffsetDateTime connectedAt,
        OffsetDateTime tokenExpiresAt
) {}
