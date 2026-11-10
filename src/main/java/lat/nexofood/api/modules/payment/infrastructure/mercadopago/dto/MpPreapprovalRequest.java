package lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MpPreapprovalRequest(
        @JsonProperty("reason") String reason,
        @JsonProperty("external_reference") String externalReference,
        @JsonProperty("payer_email") String payerEmail,
        @JsonProperty("back_url") String backUrl,
        @JsonProperty("status") String status,
        @JsonProperty("auto_recurring") MpAutoRecurring autoRecurring
) {

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MpAutoRecurring(
            @JsonProperty("frequency") Integer frequency,
            @JsonProperty("frequency_type") String frequencyType,
            @JsonProperty("transaction_amount") BigDecimal transactionAmount,
            @JsonProperty("currency_id") String currencyId,
            @JsonProperty("start_date") String startDate
    ) {}
}
