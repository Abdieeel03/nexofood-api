package lat.nexofood.api.modules.identity.web.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user,
        List<TenantStaffMembershipDto> staffMemberships
) {
}
