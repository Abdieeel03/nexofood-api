package lat.nexofood.api.modules.payment.application.service;

import lat.nexofood.api.modules.payment.web.dto.response.MercadoPagoConnectResponse;
import lat.nexofood.api.modules.payment.web.dto.response.MercadoPagoStatusResponse;
import lat.nexofood.api.modules.store.domain.Tenant;

import java.util.UUID;

public interface MercadoPagoOAuthService {

    MercadoPagoConnectResponse buildAuthorizationUrl(UUID tenantId);

    String handleCallback(String code, String state, String error);

    MercadoPagoStatusResponse getStatus(UUID tenantId);

    void disconnect(UUID tenantId);

    String getValidAccessToken(Tenant tenant);

    boolean refreshTenantToken(Tenant tenant);
}
