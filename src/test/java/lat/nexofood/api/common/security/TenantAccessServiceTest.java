package lat.nexofood.api.common.security;

import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.domain.UserSystemRole;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantAccessServiceTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private TenantAccessService tenantAccessService;

    @Test
    @DisplayName("SUPERADMIN tiene acceso total en isMember, isOwner y hasAnyRole")
    void superAdminHasFullAccess() {
        UUID tenantId = UUID.randomUUID();
        User superAdmin = User.builder()
                .id(UUID.randomUUID())
                .email("admin@nexofood.lat")
                .systemRole(UserSystemRole.SUPERADMIN)
                .build();

        when(currentUserProvider.getCurrentUser()).thenReturn(superAdmin);

        assertTrue(tenantAccessService.isMember(tenantId));
        assertTrue(tenantAccessService.isOwner(tenantId));
        assertTrue(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.ADMIN));
    }

    @Test
    @DisplayName("isOwner retorna true cuando el usuario es owner directo o miembro con rol OWNER")
    void isOwnerReturnsTrueForOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder()
                .id(ownerId)
                .email("owner@nexofood.lat")
                .systemRole(UserSystemRole.USER)
                .build();
        Tenant tenant = Tenant.builder().id(tenantId).owner(owner).build();

        when(currentUserProvider.getCurrentUser()).thenReturn(owner);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        assertTrue(tenantAccessService.isOwner(tenantId));
        assertTrue(tenantAccessService.isMember(tenantId));
    }

    @Test
    @DisplayName("isOwner retorna false para empleado que no es OWNER")
    void isOwnerReturnsFalseForRegularStaff() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        User staff = User.builder()
                .id(staffId)
                .email("cocinero@nexofood.lat")
                .systemRole(UserSystemRole.USER)
                .build();
        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .owner(User.builder().id(UUID.randomUUID()).build())
                .build();

        when(currentUserProvider.getCurrentUser()).thenReturn(staff);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantMemberRepository.existsByTenantIdAndUserIdAndIsActiveTrueAndRoleIn(
                eq(tenantId), eq(staffId), any()
        )).thenReturn(false);

        assertFalse(tenantAccessService.isOwner(tenantId));
    }

    @Test
    @DisplayName("hasAnyRole valida correctamente roles de TenantMember activo")
    void hasAnyRoleChecksActiveMembership() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("admin-tienda@nexofood.lat")
                .systemRole(UserSystemRole.USER)
                .build();

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());
        when(tenantMemberRepository.existsByTenantIdAndUserIdAndIsActiveTrueAndRoleIn(
                eq(tenantId),
                eq(userId),
                eq(List.of(TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
        )).thenReturn(true);

        assertTrue(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN));
    }
}
