package lat.nexofood.api.common.security;

import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.domain.UserSystemRole;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Service("tenantAccess")
@RequiredArgsConstructor
public class TenantAccessService {

    private final CurrentUserProvider currentUserProvider;
    private final TenantMemberRepository tenantMemberRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public boolean isSuperAdmin() {
        return currentUserProvider.isSuperAdmin();
    }

    @Transactional(readOnly = true)
    public boolean isMember(UUID tenantId) {
        if (tenantId == null) {
            return false;
        }
        try {
            User user = currentUserProvider.getCurrentUser();
            if (user.getSystemRole() == UserSystemRole.SUPERADMIN) {
                return true;
            }
            return isDirectOwner(tenantId, user.getId())
                    || tenantMemberRepository.existsByTenantIdAndUserIdAndIsActiveTrue(tenantId, user.getId());
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public boolean isOwner(UUID tenantId) {
        if (tenantId == null) {
            return false;
        }
        try {
            User user = currentUserProvider.getCurrentUser();
            if (user.getSystemRole() == UserSystemRole.SUPERADMIN) {
                return true;
            }
            return isDirectOwner(tenantId, user.getId())
                    || tenantMemberRepository.existsByTenantIdAndUserIdAndIsActiveTrueAndRoleIn(
                    tenantId, user.getId(), Arrays.asList(TenantStaffRole.OWNER)
            );
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public boolean hasAnyRole(UUID tenantId, TenantStaffRole... roles) {
        if (tenantId == null || roles == null || roles.length == 0) {
            return false;
        }
        try {
            User user = currentUserProvider.getCurrentUser();
            if (user.getSystemRole() == UserSystemRole.SUPERADMIN) {
                return true;
            }
            boolean ownerIncluded = Arrays.asList(roles).contains(TenantStaffRole.OWNER);
            if (ownerIncluded && isDirectOwner(tenantId, user.getId())) {
                return true;
            }
            return tenantMemberRepository.existsByTenantIdAndUserIdAndIsActiveTrueAndRoleIn(
                    tenantId, user.getId(), Arrays.asList(roles)
            );
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public Optional<TenantStaffRole> getCurrentUserRoleInTenant(UUID tenantId) {
        User user = currentUserProvider.getCurrentUser();
        if (isDirectOwner(tenantId, user.getId())) {
            return Optional.of(TenantStaffRole.OWNER);
        }
        return tenantMemberRepository.findByTenantIdAndUserIdAndIsActiveTrue(tenantId, user.getId())
                .map(member -> member.getRole());
    }

    private boolean isDirectOwner(UUID tenantId, UUID userId) {
        return tenantRepository.findById(tenantId)
                .map(tenant -> tenant.getOwner() != null && userId.equals(tenant.getOwner().getId()))
                .orElse(false);
    }
}
