package lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record MpWebhookNotification(
        @JsonProperty("id") Object id,
        @JsonProperty("live_mode") Boolean liveMode,
        @JsonProperty("type") String type,
        @JsonProperty("topic") String topic,
        @JsonProperty("date_created") String dateCreated,
        @JsonProperty("user_id") String userId,
        @JsonProperty("api_version") String apiVersion,
        @JsonProperty("action") String action,
        @JsonProperty("data") MpWebhookData data
) {

    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MpWebhookData(
            @JsonProperty("id") String id
    ) {}

    public String resolvedType() {
        if (type != null && !type.isBlank()) {
            return type;
        }
        return topic;
    }

    public String resolvedDataId() {
        if (data != null && data.id() != null && !data.id().isBlank()) {
            return data.id();
        }
        return null;
    }
}
