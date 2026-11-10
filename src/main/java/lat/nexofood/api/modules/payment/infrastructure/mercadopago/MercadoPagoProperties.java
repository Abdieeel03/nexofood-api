package lat.nexofood.api.modules.payment.infrastructure.mercadopago;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mercadopago")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MercadoPagoProperties {

    @Builder.Default
    private String apiBaseUrl = "https://api.mercadopago.com";

    @Builder.Default
    private String authBaseUrl = "https://auth.mercadopago.com.pe";

    private String clientId;

    private String clientSecret;

    private String platformAccessToken;

    private String testPayerEmail;

    private String webhookSecret;

    @Builder.Default
    private OAuth oauth = new OAuth();

    @Builder.Default
    private String currency = "PEN";

    @Builder.Default
    private String orderExpiration = "PT30M";

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OAuth {
        @Builder.Default
        private String redirectUri = "http://localhost:8080/api/v1/mercadopago/oauth/callback";

        @Builder.Default
        private String stateSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

        @Builder.Default
        private int refreshBeforeDays = 30;
    }
}
