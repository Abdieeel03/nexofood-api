package lat.nexofood.api.modules.store.application.service.member;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.UserRepository;
import lat.nexofood.api.modules.notification.application.service.NotificationService;
import lat.nexofood.api.modules.notification.domain.NotificationActionStatus;
import lat.nexofood.api.modules.notification.domain.NotificationType;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantInvitation;
import lat.nexofood.api.modules.store.domain.TenantInvitationStatus;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantInvitationRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.store.web.dto.request.TenantMemberRequest;
import lat.nexofood.api.modules.store.web.dto.response.TenantMemberResponse;
import lat.nexofood.api.modules.store.web.mapper.TenantMemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddTenantMemberServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantMemberRepository tenantMemberRepository;
    @Mock
    private TenantInvitationRepository tenantInvitationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantAccessService tenantAccessService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private NotificationService notificationService;

    private AddTenantMemberService addTenantMemberService;

    @BeforeEach
    void setUp() {
        addTenantMemberService = new AddTenantMemberService(
                tenantRepository,
                tenantMemberRepository,
                tenantInvitationRepository,
                userRepository,
                new TenantMemberMapper(),
                tenantAccessService,
                currentUserProvider,
                notificationService
        );
    }

    @Test
    @DisplayName("Invitar por correo crea una TenantInvitation PENDING y envía notificación STAFF_INVITATION sin agregar miembro aún")
    @SuppressWarnings("unchecked")
    void invitesUserByEmailCreatesInvitationAndNotificationWithoutAddingMember() {
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID invitedUserId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();

        User owner = User.builder().id(ownerId).email("owner@gmail.com").fullName("Dueño").build();
        User invited = User.builder().id(invitedUserId).email("cocinero@gmail.com").fullName("Carlos Chef").build();
        Tenant tenant = Tenant.builder().id(tenantId).name("Pollería Nexo").owner(owner).build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmailIgnoreCase("cocinero@gmail.com")).thenReturn(Optional.of(invited));
        when(tenantMemberRepository.existsByTenantIdAndUserId(tenantId, invitedUserId)).thenReturn(false);
        when(tenantInvitationRepository.existsByTenantIdAndInvitedUserIdAndStatus(
                tenantId, invitedUserId, TenantInvitationStatus.PENDING)).thenReturn(false);
        when(currentUserProvider.getCurrentUser()).thenReturn(owner);
        when(tenantInvitationRepository.save(any(TenantInvitation.class))).thenAnswer(inv -> {
            TenantInvitation entity = inv.getArgument(0);
            entity.setId(invitationId);
            return entity;
        });

        TenantMemberRequest request = TenantMemberRequest.builder()
                .email("  COCINERO@gmail.com ")
                .role(TenantStaffRole.COCINERO)
                .build();

        TenantMemberResponse response = addTenantMemberService.execute(tenantId, request);

        assertNotNull(response);
        assertEquals(invitationId, response.id());
        assertEquals(invitedUserId, response.userId());
        assertEquals("cocinero@gmail.com", response.userEmail());
        assertEquals(TenantStaffRole.COCINERO, response.role());
        assertFalse(response.isActive());
        assertEquals(TenantInvitationStatus.PENDING, response.invitationStatus());

        verify(tenantMemberRepository, never()).save(any());

        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).createNotification(
                eq(invited),
                eq(tenant),
                eq(NotificationType.STAFF_INVITATION),
                anyString(),
                anyString(),
                eq(true),
                eq(NotificationActionStatus.PENDING),
                eq(invitationId),
                eq("STAFF_INVITATION:" + invitationId),
                metadataCaptor.capture()
        );

        Map<String, Object> metadata = metadataCaptor.getValue();
        assertEquals(invitationId.toString(), metadata.get("invitationId"));
        assertEquals(List.of("ACCEPT", "REJECT"), metadata.get("allowedActions"));
    }

    @Test
    @DisplayName("Rechaza invitación con ResourceConflictException si ya existe invitación PENDING para el usuario")
    void rejectsDuplicatePendingInvitation() {
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID invitedUserId = UUID.randomUUID();

        User owner = User.builder().id(ownerId).email("owner@gmail.com").build();
        User invited = User.builder().id(invitedUserId).email("staff@gmail.com").build();
        Tenant tenant = Tenant.builder().id(tenantId).name("Pollería Nexo").owner(owner).build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmailIgnoreCase("staff@gmail.com")).thenReturn(Optional.of(invited));
        when(tenantMemberRepository.existsByTenantIdAndUserId(tenantId, invitedUserId)).thenReturn(false);
        when(tenantInvitationRepository.existsByTenantIdAndInvitedUserIdAndStatus(
                tenantId, invitedUserId, TenantInvitationStatus.PENDING)).thenReturn(true);

        TenantMemberRequest request = TenantMemberRequest.builder()
                .email("staff@gmail.com")
                .role(TenantStaffRole.REPARTIDOR)
                .build();

        assertThrows(ResourceConflictException.class, () -> addTenantMemberService.execute(tenantId, request));
        verify(tenantInvitationRepository, never()).save(any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(),
                eq(true), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Rechaza invitación con ResourceConflictException si el usuario ya es miembro activo")
    void rejectsWhenUserAlreadyMember() {
        UUID tenantId = UUID.randomUUID();
        UUID invitedUserId = UUID.randomUUID();

        User invited = User.builder().id(invitedUserId).email("staff@gmail.com").build();
        Tenant tenant = Tenant.builder().id(tenantId).name("Pollería Nexo").build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmailIgnoreCase("staff@gmail.com")).thenReturn(Optional.of(invited));
        when(tenantMemberRepository.existsByTenantIdAndUserId(tenantId, invitedUserId)).thenReturn(true);

        TenantMemberRequest request = TenantMemberRequest.builder()
                .email("staff@gmail.com")
                .role(TenantStaffRole.COCINERO)
                .build();

        assertThrows(ResourceConflictException.class, () -> addTenantMemberService.execute(tenantId, request));
        verify(tenantInvitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lanza ResourceNotFoundException si el correo no corresponde a ningún usuario")
    void throwsNotFoundWhenEmailDoesNotExist() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().id(tenantId).name("Pollería Nexo").build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmailIgnoreCase("noexiste@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("noexiste@gmail.com")).thenReturn(Optional.empty());

        TenantMemberRequest request = TenantMemberRequest.builder()
                .email("noexiste@gmail.com")
                .role(TenantStaffRole.COCINERO)
                .build();

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> addTenantMemberService.execute(tenantId, request));
        assertEquals(ErrorMessages.USER_NOT_FOUND, ex.getMessage());
    }
}
