package lat.nexofood.api.modules.payment.infrastructure.mercadopago;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.frontend")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FrontendProperties {

    @Builder.Default
    private String baseUrl = "http://localhost:5173";

    @Builder.Default
    private String orderResultPath = "/store/{slug}/checkout/result";

    @Builder.Default
    private String subscriptionResultPath = "/dashboard/subscription/result";

    @Builder.Default
    private String mpConnectResultPath = "/dashboard/settings/payments";

    public String buildOrderResultUrl(String slug, String paymentId, String outcome) {
        String path = orderResultPath.replace("{slug}", slug != null ? slug : "");
        return normalizeBaseUrl() + path + "?paymentId=" + paymentId + "&status=" + outcome;
    }

    public String buildSubscriptionResultUrl(String subscriptionId) {
        return normalizeBaseUrl() + subscriptionResultPath + "?subscriptionId=" + subscriptionId;
    }

    public String buildMpConnectResultUrl(boolean success, String reason) {
        String base = normalizeBaseUrl() + mpConnectResultPath;
        if (success) {
            return base + "?mp=connected";
        }
        return base + "?mp=error" + (reason != null && !reason.isBlank() ? "&reason=" + reason : "");
    }

    private String normalizeBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:5173";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
