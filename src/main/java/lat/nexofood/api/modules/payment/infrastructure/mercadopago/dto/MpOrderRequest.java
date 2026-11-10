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
            @JsonProperty("email") String email
    ) {}

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MpItem(
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("unit_price") String unitPrice,
            @JsonProperty("quantity") Integer quantity
    ) {}

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MpConfig(
            @JsonProperty("online") MpOnlineConfig online
    ) {}

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MpOnlineConfig(
            @JsonProperty("back_urls") MpBackUrls backUrls,
            @JsonProperty("auto_return") String autoReturn
    ) {}

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MpBackUrls(
            @JsonProperty("success") String success,
            @JsonProperty("pending") String pending,
            @JsonProperty("failure") String failure
    ) {}
}
