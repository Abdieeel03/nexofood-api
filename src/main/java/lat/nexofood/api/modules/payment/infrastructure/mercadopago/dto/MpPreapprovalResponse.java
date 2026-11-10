package lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record MpPreapprovalResponse(
        @JsonProperty("id") String id,
        @JsonProperty("status") String status,
        @JsonProperty("reason") String reason,
        @JsonProperty("external_reference") String externalReference,
        @JsonProperty("init_point") String initPoint,
        @JsonProperty("sandbox_init_point") String sandboxInitPoint,
        @JsonProperty("payer_email") String payerEmail,
        @JsonProperty("next_payment_date") String nextPaymentDate
) {}
