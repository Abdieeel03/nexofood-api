package lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record MpOrderResponse(
        @JsonProperty("id") String id,
        @JsonProperty("type") String type,
        @JsonProperty("processing_mode") String processingMode,
        @JsonProperty("status") String status,
        @JsonProperty("status_detail") String statusDetail,
        @JsonProperty("external_reference") String externalReference,
        @JsonProperty("total_amount") BigDecimal totalAmount,
        @JsonProperty("total_paid_amount") BigDecimal totalPaidAmount,
        @JsonProperty("checkout_url") String checkoutUrl,
        @JsonProperty("created_date") String createdDate,
        @JsonProperty("last_updated_date") String lastUpdatedDate,
        @JsonProperty("transactions") MpTransactions transactions,
        String rawJson
) {

    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MpTransactions(
            @JsonProperty("payments") List<MpTransactionPayment> payments
    ) {}

    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MpTransactionPayment(
            @JsonProperty("id") String id,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("paid_amount") BigDecimal paidAmount,
            @JsonProperty("status") String status,
            @JsonProperty("status_detail") String statusDetail,
            @JsonProperty("payment_method") MpPaymentMethod paymentMethod
    ) {}

    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MpPaymentMethod(
            @JsonProperty("id") String id,
            @JsonProperty("type") String type
    ) {}

    public MpOrderResponse withRawJson(String raw) {
        return new MpOrderResponse(
                id, type, processingMode, status, statusDetail, externalReference,
                totalAmount, totalPaidAmount, checkoutUrl, createdDate, lastUpdatedDate,
                transactions, raw
        );
    }

    public String firstPaymentId() {
        if (transactions != null && transactions.payments() != null && !transactions.payments().isEmpty()) {
            return transactions.payments().getFirst().id();
        }
        return null;
    }

    public String firstPaymentMethodId() {
        if (transactions != null && transactions.payments() != null && !transactions.payments().isEmpty()) {
            MpTransactionPayment first = transactions.payments().getFirst();
            if (first.paymentMethod() != null) {
                if (first.paymentMethod().id() != null && !first.paymentMethod().id().isBlank()) {
                    return first.paymentMethod().id();
                }
                return first.paymentMethod().type();
            }
        }
        return null;
    }
}
