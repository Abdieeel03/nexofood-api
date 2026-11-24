package lat.nexofood.api.modules.store.application.service.member;

import lat.nexofood.api.common.config.TenantConnectionPreparer;
import lat.nexofood.api.common.exception.ForbiddenException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.notification.application.service.NotificationService;
import lat.nexofood.api.modules.notification.domain.NotificationActionStatus;
import lat.nexofood.api.modules.notification.domain.NotificationType;
import lat.nexofood.api.modules.notification.web.dto.request.InvitationActionType;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantInvitation;
import lat.nexofood.api.modules.store.domain.TenantInvitationStatus;
import lat.nexofood.api.modules.store.domain.TenantMember;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantInvitationRepository;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantInvitationDecisionServiceTest {

    @Mock
    private TenantInvitationRepository tenantInvitationRepository;
    @Mock
    private TenantMemberRepository tenantMemberRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private TenantConnectionPreparer tenantConnectionPreparer;

    private TenantInvitationDecisionService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new TenantInvitationDecisionService(
                tenantInvitationRepository,
                tenantMemberRepository,
                notificationService,
                tenantConnectionPreparer
        );
    }

    @Test
    @DisplayName("ACCEPT marca invitación como ACCEPTED, prepara RLS, crea TenantMember y notifica INVITATION_ACCEPTED al dueño")
    void acceptCreatesMemberAndNotifiesOwner() {
        UUID invitationId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID invitedUserId = UUID.randomUUID();

        User owner = User.builder().id(ownerId).email("owner@gmail.com").fullName("Dueño").build();
        User invited = User.builder().id(invitedUserId).email("chef@gmail.com").fullName("Chef").build();
        Tenant tenant = Tenant.builder().id(tenantId).name("Pollería Nexo").owner(owner).build();

        TenantInvitation invitation = TenantInvitation.builder()
                .id(invitationId)
                .tenant(tenant)
                .invitedUser(invited)
                .inviter(owner)
                .invitedEmail("chef@gmail.com")
                .role(TenantStaffRole.COCINERO)
                .status(TenantInvitationStatus.PENDING)
                .build();

        when(tenantInvitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(tenantInvitationRepository.save(any(TenantInvitation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantMemberRepository.existsByTenantIdAndUserId(tenantId, invitedUserId)).thenReturn(false);

        decisionService.onInvitationDecision(invitationId, invitedUserId, InvitationActionType.ACCEPT);

        assertEquals(TenantInvitationStatus.ACCEPTED, invitation.getStatus());
        assertNotNull(invitation.getRespondedAt());

        verify(tenantConnectionPreparer).setTenantContext(tenantId);

        ArgumentCaptor<TenantMember> memberCaptor = ArgumentCaptor.forClass(TenantMember.class);
        verify(tenantMemberRepository).save(memberCaptor.capture());
        TenantMember createdMember = memberCaptor.getValue();
        assertEquals(tenant, createdMember.getTenant());
        assertEquals(invited, createdMember.getUser());
        assertEquals(TenantStaffRole.COCINERO, createdMember.getRole());
        assertTrue(createdMember.getIsActive());

        verify(notificationService).createNotification(
                eq(owner),
                eq(tenant),
                eq(NotificationType.INVITATION_ACCEPTED),
                anyString(),
                anyString(),
                eq(false),
                eq(NotificationActionStatus.NONE),
                eq(invitationId),
                eq("INVITATION_ACCEPTED:" + invitationId),
                anyMap()
        );
    }

    @Test
    @DisplayName("REJECT marca invitación como REJECTED, NO crea TenantMember y notifica INVITATION_REJECTED al dueño")
    void rejectDoesNotCreateMemberAndNotifiesOwner() {
        UUID invitationId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID invitedUserId = UUID.randomUUID();

        User owner = User.builder().id(ownerId).email("owner@gmail.com").fullName("Dueño").build();
        User invited = User.builder().id(invitedUserId).email("driver@gmail.com").fullName("Driver").build();
        Tenant tenant = Tenant.builder().id(tenantId).name("Pollería Nexo").owner(owner).build();

        TenantInvitation invitation = TenantInvitation.builder()
                .id(invitationId)
                .tenant(tenant)
                .invitedUser(invited)
                .inviter(owner)
                .invitedEmail("driver@gmail.com")
                .role(TenantStaffRole.REPARTIDOR)
                .status(TenantInvitationStatus.PENDING)
                .build();

        when(tenantInvitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(tenantInvitationRepository.save(any(TenantInvitation.class))).thenAnswer(inv -> inv.getArgument(0));

        decisionService.onInvitationDecision(invitationId, invitedUserId, InvitationActionType.REJECT);

        assertEquals(TenantInvitationStatus.REJECTED, invitation.getStatus());
        assertNotNull(invitation.getRespondedAt());
        verify(tenantMemberRepository, never()).save(any());

        verify(notificationService).createNotification(
                eq(owner),
                eq(tenant),
                eq(NotificationType.INVITATION_REJECTED),
                anyString(),
                anyString(),
                eq(false),
                eq(NotificationActionStatus.NONE),
                eq(invitationId),
                eq("INVITATION_REJECTED:" + invitationId),
                anyMap()
        );
    }

    @Test
    @DisplayName("Lanza ForbiddenException si otro usuario intenta responder la invitación")
    void throwsForbiddenWhenDifferentUserResponds() {
        UUID invitationId = UUID.randomUUID();
        User invited = User.builder().id(UUID.randomUUID()).email("chef@gmail.com").build();
        TenantInvitation invitation = TenantInvitation.builder()
                .id(invitationId)
                .invitedUser(invited)
                .status(TenantInvitationStatus.PENDING)
                .build();

        when(tenantInvitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));

        assertThrows(ForbiddenException.class, () ->
                decisionService.onInvitationDecision(invitationId, UUID.randomUUID(), InvitationActionType.ACCEPT));
        verify(tenantMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lanza ResourceConflictException si la invitación ya no está en estado PENDING")
    void throwsConflictWhenInvitationAlreadyResponded() {
        UUID invitationId = UUID.randomUUID();
        UUID invitedUserId = UUID.randomUUID();
        User invited = User.builder().id(invitedUserId).email("chef@gmail.com").build();
        TenantInvitation invitation = TenantInvitation.builder()
                .id(invitationId)
                .invitedUser(invited)
                .status(TenantInvitationStatus.ACCEPTED)
                .build();

        when(tenantInvitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));

        assertThrows(ResourceConflictException.class, () ->
                decisionService.onInvitationDecision(invitationId, invitedUserId, InvitationActionType.REJECT));
    }
}
