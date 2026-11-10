package lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record MpAuthorizedPaymentResponse(
        @JsonProperty("id") String id,
        @JsonProperty("preapproval_id") String preapprovalId,
        @JsonProperty("external_reference") String externalReference,
        @JsonProperty("status") String status,
        @JsonProperty("transaction_amount") BigDecimal transactionAmount,
        @JsonProperty("currency_id") String currencyId,
        @JsonProperty("date_created") String dateCreated,
        @JsonProperty("payment") MpPaymentSummary payment,
        String rawJson
) {

    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MpPaymentSummary(
            @JsonProperty("id") String id,
            @JsonProperty("status") String status,
            @JsonProperty("status_detail") String statusDetail
    ) {}

    public MpAuthorizedPaymentResponse withRawJson(String raw) {
        return new MpAuthorizedPaymentResponse(
                id, preapprovalId, externalReference, status,
                transactionAmount, currencyId, dateCreated, payment, raw
        );
    }

    public String effectiveStatus() {
        if (payment != null && payment.status() != null && !payment.status().isBlank()) {
            return payment.status();
        }
        return status;
    }

    public String paymentId() {
        return payment != null ? payment.id() : null;
    }
}
