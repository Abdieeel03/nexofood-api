package lat.nexofood.api.modules.store.web.dto.response;

import lat.nexofood.api.modules.identity.web.dto.response.UserResponse;
import lat.nexofood.api.modules.store.web.dto.response.TenantCustomerResponse;
import lombok.Builder;

@Builder
public record StoreAuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user,
        TenantCustomerResponse customerProfile
) {}
