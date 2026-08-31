package lat.nexofood.api.modules.tenant.mapper;

import lat.nexofood.api.modules.auth.domain.User;
import lat.nexofood.api.modules.tenant.domain.Tenant;
import lat.nexofood.api.modules.tenant.domain.TenantMember;
import lat.nexofood.api.modules.tenant.domain.TenantStaffRole;
import lat.nexofood.api.modules.tenant.dto.response.TenantMemberResponse;
import org.springframework.stereotype.Component;

@Component
public class TenantMemberMapper {

    public TenantMemberResponse toResponse(TenantMember member) {
        if (member == null) {
            return null;
        }
        return TenantMemberResponse.builder()
                .id(member.getId())
                .tenantId(member.getTenant() != null ? member.getTenant().getId() : null)
                .userId(member.getUser() != null ? member.getUser().getId() : null)
                .userFullName(member.getUser() != null ? member.getUser().getFullName() : null)
                .userEmail(member.getUser() != null ? member.getUser().getEmail() : null)
                .role(member.getRole())
                .isActive(member.getIsActive())
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }

    public TenantMember toEntity(Tenant tenant, User user, TenantStaffRole role) {
        return TenantMember.builder()
                .tenant(tenant)
                .user(user)
                .role(role)
                .build();
    }
}
