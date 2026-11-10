package lat.nexofood.api.modules.payment.web.dto.response;

import lombok.Builder;

@Builder
public record MercadoPagoConnectResponse(
        String authorizationUrl
) {}
