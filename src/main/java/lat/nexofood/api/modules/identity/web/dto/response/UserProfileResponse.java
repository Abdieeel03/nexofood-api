package lat.nexofood.api.modules.identity.web.dto.response;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        String systemRole,
        Boolean isActive,
        List<TenantStaffMembershipDto> staffMemberships,
        List<CustomerAddressResponse> addresses
) {}
