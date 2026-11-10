package lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MpOrderRequest(
        @JsonProperty("type") String type,
        @JsonProperty("processing_mode") String processingMode,
        @JsonProperty("total_amount") String totalAmount,
        @JsonProperty("external_reference") String externalReference,
        @JsonProperty("expiration_time") String expirationTime,
        @JsonProperty("payer") MpPayer payer,
        @JsonProperty("items") List<MpItem> items,
        @JsonProperty("config") MpConfig config
) {

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MpPayer(
            @JsonProperty("email") String email,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName
    ) {}

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MpItem(
            @JsonProperty("external_code") String externalCode,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("category_id") String categoryId,
            @JsonProperty("unit_price") String unitPrice,
            @JsonProperty("quantity") Integer quantity
    ) {}

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MpConfig(
            @JsonProperty("statement_descriptor") String statementDescriptor,
            @JsonProperty("online") MpOnlineConfig online
    ) {}

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MpOnlineConfig(
            @JsonProperty("success_url") String successUrl,
            @JsonProperty("pending_url") String pendingUrl,
            @JsonProperty("failure_url") String failureUrl,
            @JsonProperty("auto_return") String autoReturn
    ) {}
}
