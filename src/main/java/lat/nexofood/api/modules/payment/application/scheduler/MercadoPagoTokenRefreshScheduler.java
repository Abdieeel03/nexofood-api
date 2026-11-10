package lat.nexofood.api.modules.payment.application.scheduler;

import lat.nexofood.api.modules.payment.application.service.MercadoPagoOAuthService;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoProperties;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MercadoPagoTokenRefreshScheduler {

    private final TenantRepository tenantRepository;
    private final MercadoPagoOAuthService oauthService;
    private final MercadoPagoProperties properties;

    @Scheduled(cron = "${mercadopago.token-refresh-cron:0 30 3 * * *}")
    public int refreshExpiringTokens() {
        int days = properties.getOauth() != null && properties.getOauth().getRefreshBeforeDays() > 0
                ? properties.getOauth().getRefreshBeforeDays()
                : 30;
        OffsetDateTime threshold = OffsetDateTime.now().plusDays(days);

        List<Tenant> expiringTenants = tenantRepository
                .findAllByMpRefreshTokenIsNotNullAndMpTokenExpiresAtBefore(threshold);

        int refreshedCount = 0;
        for (Tenant tenant : expiringTenants) {
            if (oauthService.refreshTenantToken(tenant)) {
                refreshedCount++;
            }
        }
        return refreshedCount;
    }
}
