package lat.nexofood.api.modules.store.web.mapper;

import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantInvitation;
import lat.nexofood.api.modules.store.domain.TenantInvitationStatus;
import lat.nexofood.api.modules.store.domain.TenantMember;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.web.dto.response.TenantMemberResponse;
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

    public TenantMemberResponse toInvitationResponse(TenantInvitation invitation) {
        if (invitation == null) {
            return null;
        }
        User invitedUser = invitation.getInvitedUser();
        return TenantMemberResponse.builder()
                .id(invitation.getId())
                .tenantId(invitation.getTenant() != null ? invitation.getTenant().getId() : null)
                .userId(invitedUser != null ? invitedUser.getId() : null)
                .userFullName(invitedUser != null ? invitedUser.getFullName() : null)
                .userEmail(invitedUser != null ? invitedUser.getEmail() : invitation.getInvitedEmail())
                .role(invitation.getRole())
                .isActive(invitation.getStatus() == TenantInvitationStatus.ACCEPTED)
                .createdAt(invitation.getCreatedAt())
                .updatedAt(invitation.getUpdatedAt())
                .invitationStatus(invitation.getStatus())
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
