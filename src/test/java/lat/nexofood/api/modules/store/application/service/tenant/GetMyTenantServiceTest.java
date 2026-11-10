package lat.nexofood.api.modules.store.application.service.tenant;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantMember;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMapper;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetMyTenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantMapper tenantMapper;

    @InjectMocks
    private GetMyTenantService getMyTenantService;

    @Test
    @DisplayName("Retorna el restaurante cuando el usuario es dueño directo")
    void returnsTenantWhenUserIsOwner() {
        String email = "owner@nexofood.lat";
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        User user = User.builder().id(userId).email(email).build();
        Tenant tenant = Tenant.builder().id(tenantId).name("Mi Restaurante").build();
        TenantResponse response = TenantResponse.builder().id(tenantId).name("Mi Restaurante").build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(tenantRepository.findByOwnerId(userId)).thenReturn(Optional.of(tenant));
        when(tenantMapper.toResponse(tenant)).thenReturn(response);

        TenantResponse result = getMyTenantService.execute(email);

        assertNotNull(result);
        assertEquals(tenantId, result.id());
        assertEquals("Mi Restaurante", result.name());
        verify(tenantMemberRepository, never()).findAllByUserId(any());
    }

    @Test
    @DisplayName("Retorna el restaurante cuando el usuario es miembro activo del staff")
    void returnsTenantWhenUserIsStaffMember() {
        String email = "staff@nexofood.lat";
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        User user = User.builder().id(userId).email(email).build();
        Tenant tenant = Tenant.builder().id(tenantId).name("Restaurante Staff").build();
        TenantMember member = TenantMember.builder()
                .tenant(tenant)
                .user(user)
                .role(TenantStaffRole.COCINERO)
                .isActive(true)
                .build();
        TenantResponse response = TenantResponse.builder().id(tenantId).name("Restaurante Staff").build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(tenantRepository.findByOwnerId(userId)).thenReturn(Optional.empty());
        when(tenantMemberRepository.findAllByUserId(userId)).thenReturn(List.of(member));
        when(tenantMapper.toResponse(tenant)).thenReturn(response);

        TenantResponse result = getMyTenantService.execute(email);

        assertNotNull(result);
        assertEquals(tenantId, result.id());
        assertEquals("Restaurante Staff", result.name());
    }

    @Test
    @DisplayName("Retorna null (sin excepción 404) cuando el usuario no tiene ninguna tienda")
    void returnsNullWhenUserHasNoTenant() {
        String email = "cliente@nexofood.lat";
        UUID userId = UUID.randomUUID();

        User user = User.builder().id(userId).email(email).build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(tenantRepository.findByOwnerId(userId)).thenReturn(Optional.empty());
        when(tenantMemberRepository.findAllByUserId(userId)).thenReturn(List.of());

        TenantResponse result = getMyTenantService.execute(email);

        assertNull(result);
    }

    @Test
    @DisplayName("Ignora membresías inactivas y retorna null si no tiene tiendas activas")
    void ignoresInactiveMembershipsAndReturnsNull() {
        String email = "ex-staff@nexofood.lat";
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        User user = User.builder().id(userId).email(email).build();
        Tenant tenant = Tenant.builder().id(tenantId).name("Restaurante Pasado").build();
        TenantMember inactiveMember = TenantMember.builder()
                .tenant(tenant)
                .user(user)
                .role(TenantStaffRole.COCINERO)
                .isActive(false)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(tenantRepository.findByOwnerId(userId)).thenReturn(Optional.empty());
        when(tenantMemberRepository.findAllByUserId(userId)).thenReturn(List.of(inactiveMember));

        TenantResponse result = getMyTenantService.execute(email);

        assertNull(result);
    }

    @Test
    @DisplayName("Lanza ResourceNotFoundException si el usuario no existe en la base de datos")
    void throwsNotFoundWhenUserDoesNotExist() {
        String email = "nonexistent@nexofood.lat";

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> getMyTenantService.execute(email));

        assertEquals(ErrorMessages.USER_NOT_FOUND, ex.getMessage());
    }
}
