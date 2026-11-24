package lat.nexofood.api.modules.notification.application.service;

import lat.nexofood.api.common.exception.UnauthorizedException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.notification.domain.Notification;
import lat.nexofood.api.modules.notification.domain.NotificationActionStatus;
import lat.nexofood.api.modules.notification.domain.NotificationType;
import lat.nexofood.api.modules.notification.infrastructure.repository.NotificationRepository;
import lat.nexofood.api.modules.notification.web.dto.request.InvitationActionType;
import lat.nexofood.api.modules.notification.web.dto.request.NotificationInvitationDecisionRequest;
import lat.nexofood.api.modules.notification.web.dto.response.NotificationResponse;
import lat.nexofood.api.modules.notification.web.dto.response.NotificationStreamTicketResponse;
import lat.nexofood.api.modules.notification.web.mapper.NotificationMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSseServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private TenantMemberRepository tenantMemberRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ObjectProvider<NotificationInvitationResponder> invitationResponderProvider;
    @Mock
    private NotificationSseService mockSseService;

    private NotificationSseTicketService ticketService;
    private NotificationSseService sseService;
    private NotificationServiceImpl notificationService;

    private UUID userId;
    private User user;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        ticketService = new NotificationSseTicketService();
        sseService = new NotificationSseService();
        notificationService = new NotificationServiceImpl(
                notificationRepository,
                new NotificationMapper(),
                tenantMemberRepository,
                currentUserProvider,
                null,
                invitationResponderProvider,
                mockSseService,
                ticketService
        );

        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email("user@nexofood.lat")
                .fullName("Test User")
                .isActive(true)
                .build();
        tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Burger Bros")
                .slug("burger-bros")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("NotificationSseTicketService emite tickets de un solo uso y rechaza tickets inválidos o reutilizados")
    void ticketServiceIssuesAndConsumesSingleUseTicket() {
        NotificationStreamTicketResponse issued = ticketService.issueTicket(userId);

        assertNotNull(issued.ticket());
        assertFalse(issued.ticket().isBlank());
        assertEquals(30L, issued.expiresInSeconds());
        assertNotNull(issued.expiresAt());

        // Primera consumición exitosa
        Optional<UUID> consumed = ticketService.consumeTicket(issued.ticket());
        assertEquals(Optional.of(userId), consumed);

        // Segunda consumición del mismo ticket debe fallar (single-use)
        assertTrue(ticketService.consumeTicket(issued.ticket()).isEmpty());
        assertTrue(ticketService.consumeTicket("ticket-inexistente").isEmpty());
        assertTrue(ticketService.consumeTicket("   ").isEmpty());
    }

    @Test
    @DisplayName("NotificationSseService gestiona múltiples conexiones por usuario y respeta límite máximo")
    void sseServiceTracksConnectionsAndEnforcesLimit() {
        sseService.setMaxConnectionsPerUser(2);

        SseEmitter e1 = sseService.subscribe(userId, 3L);
        assertNotNull(e1);
        assertTrue(sseService.hasActiveConnections(userId));
        assertEquals(1, sseService.getActiveConnectionCount(userId));

        SseEmitter e2 = sseService.subscribe(userId, 3L);
        assertNotNull(e2);
        assertEquals(2, sseService.getActiveConnectionCount(userId));

        // Al abrir una 3ra conexión, se cierra la más antigua y el total sigue en 2
        SseEmitter e3 = sseService.subscribe(userId, 3L);
        assertNotNull(e3);
        assertEquals(2, sseService.getActiveConnectionCount(userId));

        sseService.sendHeartbeat();
        sseService.shutdown();
        assertEquals(0, sseService.getTotalActiveConnections());
        assertFalse(sseService.hasActiveConnections(userId));
    }

    @Test
    @DisplayName("subscribeToStream acepta ticket temporal de un solo uso o JWT Bearer, y rechaza peticiones sin credenciales")
    void subscribeToStreamAuthenticatesViaTicketOrJwt() {
        SseEmitter expectedEmitter = new SseEmitter();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(notificationRepository.countByRecipientIdAndIsReadFalse(userId)).thenReturn(4L);
        when(mockSseService.subscribe(userId, 4L)).thenReturn(expectedEmitter);

        // 1. Autenticación por ticket temporal
        NotificationStreamTicketResponse ticketResp = notificationService.issueStreamTicket();
        SseEmitter emitterByTicket = notificationService.subscribeToStream(ticketResp.ticket());
        assertSame(expectedEmitter, emitterByTicket);

        // Reutilizar el ticket debe lanzar UnauthorizedException
        assertThrows(UnauthorizedException.class, () -> notificationService.subscribeToStream(ticketResp.ticket()));

        // 2. Autenticación por JWT Bearer (sin ticket)
        when(currentUserProvider.getCurrentEmailOptional()).thenReturn(Optional.of(user.getEmail()));
        SseEmitter emitterByJwt = notificationService.subscribeToStream(null);
        assertSame(expectedEmitter, emitterByJwt);

        // 3. Sin ticket y sin JWT debe lanzar UnauthorizedException
        when(currentUserProvider.getCurrentEmailOptional()).thenReturn(Optional.empty());
        assertThrows(UnauthorizedException.class, () -> notificationService.subscribeToStream(null));
    }

    @Test
    @DisplayName("createNotification, markAsRead, markAllAsRead y respondToInvitation emiten eventos SSE cuando el usuario está conectado")
    void notificationLifecyclePublishesSseEvents() {
        UUID notifId = UUID.randomUUID();
        when(mockSseService.hasActiveConnections(userId)).thenReturn(true);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            if (n.getId() == null) {
                n.setId(notifId);
            }
            return n;
        });
        when(notificationRepository.countByRecipientIdAndIsReadFalse(userId)).thenReturn(2L);

        NotificationResponse created = notificationService.createNotification(
                user,
                tenant,
                NotificationType.NEW_ORDER_RECEIVED,
                "Nuevo pedido",
                "Pedido #ORD-000001",
                false,
                NotificationActionStatus.NONE,
                UUID.randomUUID(),
                null,
                Map.of("orderNumber", "ORD-000001")
        );

        assertNotNull(created);
        verify(mockSseService).sendNotification(eq(userId), eq(created), eq(2L));

        // markAsRead emite unread-count actualizado
        Notification unread = Notification.builder()
                .id(notifId)
                .recipient(user)
                .tenant(tenant)
                .type(NotificationType.NEW_ORDER_RECEIVED)
                .title("Nuevo pedido")
                .message("Pedido #ORD-000001")
                .isRead(false)
                .build();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(notificationRepository.findByIdAndRecipientId(notifId, userId)).thenReturn(Optional.of(unread));
        when(notificationRepository.countByRecipientIdAndIsReadFalse(userId)).thenReturn(1L);

        notificationService.markAsRead(notifId);
        verify(mockSseService).sendUnreadCount(userId, 1L);

        // markAllAsRead emite unread-count = 0
        Notification anotherUnread = Notification.builder()
                .id(UUID.randomUUID())
                .recipient(user)
                .type(NotificationType.LOW_STOCK)
                .title("Stock")
                .message("Bajo")
                .isRead(false)
                .build();
        when(notificationRepository.findAllByRecipientIdAndIsReadFalse(userId)).thenReturn(List.of(anotherUnread));

        notificationService.markAllAsRead();
        verify(mockSseService).sendUnreadCount(userId, 0L);

        // respondToInvitation emite unread-count actualizado
        UUID inviteNotifId = UUID.randomUUID();
        Notification invitation = Notification.builder()
                .id(inviteNotifId)
                .recipient(user)
                .tenant(tenant)
                .type(NotificationType.STAFF_INVITATION)
                .title("Invitación")
                .message("Únete")
                .actionRequired(true)
                .actionStatus(NotificationActionStatus.PENDING)
                .referenceId(UUID.randomUUID())
                .isRead(false)
                .build();
        when(notificationRepository.findByIdAndRecipientId(inviteNotifId, userId)).thenReturn(Optional.of(invitation));
        when(invitationResponderProvider.getIfAvailable()).thenReturn(null);
        when(notificationRepository.countByRecipientIdAndIsReadFalse(userId)).thenReturn(0L);

        notificationService.respondToInvitation(
                inviteNotifId,
                new NotificationInvitationDecisionRequest(InvitationActionType.ACCEPT)
        );
        verify(mockSseService, times(2)).sendUnreadCount(userId, 0L);
    }
}
