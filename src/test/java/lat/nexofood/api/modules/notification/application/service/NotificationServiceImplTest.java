package lat.nexofood.api.modules.notification.application.service;

import lat.nexofood.api.common.config.TenantConnectionPreparer;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.notification.domain.Notification;
import lat.nexofood.api.modules.notification.domain.NotificationActionStatus;
import lat.nexofood.api.modules.notification.domain.NotificationType;
import lat.nexofood.api.modules.notification.infrastructure.repository.NotificationRepository;
import lat.nexofood.api.modules.notification.web.dto.request.InvitationActionType;
import lat.nexofood.api.modules.notification.web.dto.request.NotificationInvitationDecisionRequest;
import lat.nexofood.api.modules.notification.web.dto.response.NotificationResponse;
import lat.nexofood.api.modules.notification.web.dto.response.UnreadNotificationCountResponse;
import lat.nexofood.api.modules.notification.web.mapper.NotificationMapper;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.domain.TenantMember;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private TenantMemberRepository tenantMemberRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private TenantConnectionPreparer tenantConnectionPreparer;
    @Mock
    private ObjectProvider<NotificationInvitationResponder> invitationResponderProvider;
    @Mock
    private NotificationInvitationResponder invitationResponder;

    private NotificationMapper notificationMapper;
    private NotificationServiceImpl notificationService;

    private UUID recipientId;
    private User recipient;
    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        notificationMapper = new NotificationMapper();
        notificationService = new NotificationServiceImpl(
                notificationRepository,
                notificationMapper,
                tenantMemberRepository,
                currentUserProvider,
                tenantConnectionPreparer,
                invitationResponderProvider
        );

        recipientId = UUID.randomUUID();
        recipient = User.builder()
                .id(recipientId)
                .email("staff@nexofood.lat")
                .fullName("Staff User")
                .isActive(true)
                .build();

        tenantId = UUID.randomUUID();
        User owner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@nexofood.lat")
                .fullName("Owner User")
                .isActive(true)
                .build();
        tenant = Tenant.builder()
                .id(tenantId)
                .name("Burger Bros")
                .slug("burger-bros")
                .owner(owner)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("respondToInvitation: aceptar invitación delega al responder, marca ACCEPTED y la marca como leída")
    void respondToInvitationAcceptsAndMarksRead() {
        UUID notificationId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        Notification pending = Notification.builder()
                .id(notificationId)
                .recipient(recipient)
                .tenant(tenant)
                .type(NotificationType.STAFF_INVITATION)
                .title("Invitación a Burger Bros")
                .message("Te han invitado como COCINERO")
                .actionRequired(true)
                .actionStatus(NotificationActionStatus.PENDING)
                .referenceId(invitationId)
                .isRead(false)
                .build();

        when(currentUserProvider.getCurrentUserId()).thenReturn(recipientId);
        when(notificationRepository.findByIdAndRecipientId(notificationId, recipientId))
                .thenReturn(Optional.of(pending));
        when(invitationResponderProvider.getIfAvailable()).thenReturn(invitationResponder);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = notificationService.respondToInvitation(
                notificationId,
                new NotificationInvitationDecisionRequest(InvitationActionType.ACCEPT)
        );

        verify(invitationResponder).onInvitationDecision(invitationId, recipientId, InvitationActionType.ACCEPT);
        assertEquals(NotificationActionStatus.ACCEPTED, response.actionStatus());
        assertTrue(response.isRead());
        assertNotNull(response.readAt());
        assertNotNull(response.respondedAt());
        assertTrue(response.allowedActions().isEmpty());
    }

    @Test
    @DisplayName("respondToInvitation: rechazar invitación delega al responder y marca REJECTED")
    void respondToInvitationRejects() {
        UUID notificationId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        Notification pending = Notification.builder()
                .id(notificationId)
                .recipient(recipient)
                .tenant(tenant)
                .type(NotificationType.STAFF_INVITATION)
                .title("Invitación a Burger Bros")
                .message("Te han invitado como ADMIN")
                .actionRequired(true)
                .actionStatus(NotificationActionStatus.PENDING)
                .referenceId(invitationId)
                .isRead(false)
                .build();

        when(currentUserProvider.getCurrentUserId()).thenReturn(recipientId);
        when(notificationRepository.findByIdAndRecipientId(notificationId, recipientId))
                .thenReturn(Optional.of(pending));
        when(invitationResponderProvider.getIfAvailable()).thenReturn(invitationResponder);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = notificationService.respondToInvitation(
                notificationId,
                new NotificationInvitationDecisionRequest(InvitationActionType.REJECT)
        );

        verify(invitationResponder).onInvitationDecision(invitationId, recipientId, InvitationActionType.REJECT);
        assertEquals(NotificationActionStatus.REJECTED, response.actionStatus());
        assertTrue(response.isRead());
        assertNotNull(response.respondedAt());
        assertTrue(response.allowedActions().isEmpty());
    }

    @Test
    @DisplayName("respondToInvitation: lanza ResourceConflictException si ya fue respondida")
    void respondToInvitationThrowsConflictWhenAlreadyResponded() {
        UUID notificationId = UUID.randomUUID();
        Notification alreadyAccepted = Notification.builder()
                .id(notificationId)
                .recipient(recipient)
                .tenant(tenant)
                .type(NotificationType.STAFF_INVITATION)
                .title("Invitación")
                .message("Mensaje")
                .actionRequired(true)
                .actionStatus(NotificationActionStatus.ACCEPTED)
                .referenceId(UUID.randomUUID())
                .isRead(true)
                .build();

        when(currentUserProvider.getCurrentUserId()).thenReturn(recipientId);
        when(notificationRepository.findByIdAndRecipientId(notificationId, recipientId))
                .thenReturn(Optional.of(alreadyAccepted));

        assertThrows(ResourceConflictException.class, () ->
                notificationService.respondToInvitation(
                        notificationId,
                        new NotificationInvitationDecisionRequest(InvitationActionType.ACCEPT)
                )
        );

        verifyNoInteractions(invitationResponder);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("respondToInvitation: lanza BadRequestException si la notificación no es de invitación accionable")
    void respondToInvitationThrowsBadRequestForNonInvitation() {
        UUID notificationId = UUID.randomUUID();
        Notification stockAlert = Notification.builder()
                .id(notificationId)
                .recipient(recipient)
                .tenant(tenant)
                .type(NotificationType.LOW_STOCK)
                .title("Stock bajo")
                .message("Mensaje")
                .actionRequired(false)
                .actionStatus(NotificationActionStatus.NONE)
                .build();

        when(currentUserProvider.getCurrentUserId()).thenReturn(recipientId);
        when(notificationRepository.findByIdAndRecipientId(notificationId, recipientId))
                .thenReturn(Optional.of(stockAlert));

        assertThrows(BadRequestException.class, () ->
                notificationService.respondToInvitation(
                        notificationId,
                        new NotificationInvitationDecisionRequest(InvitationActionType.ACCEPT)
                )
        );
    }

    @Test
    @DisplayName("createNotification: evita spam cuando ya existe el mismo dedupKey para el destinatario")
    void createNotificationPreventsDuplicatesByDedupKey() {
        String dedupKey = "LOW_STOCK:prod-1:2026-11-22:3";
        when(notificationRepository.existsByRecipientIdAndDedupKey(recipientId, dedupKey)).thenReturn(true);

        NotificationResponse result = notificationService.createNotification(
                recipient,
                tenant,
                NotificationType.LOW_STOCK,
                "Stock bajo",
                "Quedan 3 unidades",
                false,
                NotificationActionStatus.NONE,
                UUID.randomUUID(),
                dedupKey,
                Map.of("stock", 3)
        );

        assertNull(result);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("checkAndNotifyProductStock: alerta LOW_STOCK y OUT_OF_STOCK sin duplicar al owner y filtrando roles")
    void checkAndNotifyProductStockNotifiesOperationsStaffWithoutDuplicatingOwner() {
        UUID productId = UUID.randomUUID();
        User owner = tenant.getOwner();
        User cocinero = User.builder().id(UUID.randomUUID()).email("chef@nexofood.lat").fullName("Chef").isActive(true).build();
        User inactiveCocinero = User.builder().id(UUID.randomUUID()).email("oldchef@nexofood.lat").fullName("Old Chef").isActive(true).build();
        User repartidor = User.builder().id(UUID.randomUUID()).email("driver@nexofood.lat").fullName("Driver").isActive(true).build();

        List<TenantMember> members = List.of(
                TenantMember.builder().id(UUID.randomUUID()).tenant(tenant).user(owner).role(TenantStaffRole.ADMIN).isActive(true).build(),
                TenantMember.builder().id(UUID.randomUUID()).tenant(tenant).user(cocinero).role(TenantStaffRole.COCINERO).isActive(true).build(),
                TenantMember.builder().id(UUID.randomUUID()).tenant(tenant).user(inactiveCocinero).role(TenantStaffRole.COCINERO).isActive(false).build(),
                TenantMember.builder().id(UUID.randomUUID()).tenant(tenant).user(repartidor).role(TenantStaffRole.REPARTIDOR).isActive(true).build()
        );

        when(tenantMemberRepository.findAllByTenantId(tenantId)).thenReturn(members);
        when(notificationRepository.existsByRecipientIdAndDedupKey(any(), any())).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        Product lowStockProduct = Product.builder()
                .id(productId)
                .tenant(tenant)
                .name("Burger Royal")
                .stock(3)
                .build();

        notificationService.checkAndNotifyProductStock(lowStockProduct);

        verify(tenantConnectionPreparer).setTenantContext(tenantId);
        ArgumentCaptor<Notification> lowStockCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(lowStockCaptor.capture());

        List<Notification> savedLow = lowStockCaptor.getAllValues();
        String expectedLowKey = "LOW_STOCK:" + productId + ":" + LocalDate.now() + ":3";
        assertEquals(List.of(owner.getId(), cocinero.getId()),
                savedLow.stream().map(n -> n.getRecipient().getId()).toList());
        assertTrue(savedLow.stream().allMatch(n -> n.getType() == NotificationType.LOW_STOCK));
        assertTrue(savedLow.stream().allMatch(n -> expectedLowKey.equals(n.getDedupKey())));

        reset(notificationRepository);
        when(notificationRepository.existsByRecipientIdAndDedupKey(any(), any())).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Product outOfStockProduct = Product.builder()
                .id(productId)
                .tenant(tenant)
                .name("Burger Royal")
                .stock(0)
                .build();

        notificationService.checkAndNotifyProductStock(outOfStockProduct);

        ArgumentCaptor<Notification> outCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(outCaptor.capture());
        String expectedOutKey = "OUT_OF_STOCK:" + productId + ":" + LocalDate.now();
        assertTrue(outCaptor.getAllValues().stream().allMatch(n -> n.getType() == NotificationType.OUT_OF_STOCK));
        assertTrue(outCaptor.getAllValues().stream().allMatch(n -> expectedOutKey.equals(n.getDedupKey())));

        reset(notificationRepository);
        Product healthyStockProduct = Product.builder()
                .id(productId)
                .tenant(tenant)
                .name("Burger Royal")
                .stock(12)
                .build();
        notificationService.checkAndNotifyProductStock(healthyStockProduct);
        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("notifyNewOrder y notifyOrderStatusChanged crean notificaciones correctas para staff y cliente")
    void notifyNewOrderAndStatusChangedCreateExpectedNotifications() {
        UUID orderId = UUID.randomUUID();
        User customerUser = User.builder().id(UUID.randomUUID()).email("cliente@nexofood.lat").fullName("Cliente").build();
        TenantCustomer customer = TenantCustomer.builder().tenant(tenant).user(customerUser).build();
        Order order = Order.builder()
                .id(orderId)
                .tenant(tenant)
                .customer(customer)
                .orderNumber("ORD-101")
                .status(OrderStatus.EN_PREPARACION)
                .deliveryType(DeliveryType.DELIVERY)
                .total(new BigDecimal("45.50"))
                .build();

        when(tenantMemberRepository.findAllByTenantId(tenantId)).thenReturn(List.of());
        when(notificationRepository.existsByRecipientIdAndDedupKey(any(), any())).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.notifyNewOrder(order);
        notificationService.notifyOrderStatusChanged(order);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        Notification staffNotif = captor.getAllValues().get(0);
        assertEquals(tenant.getOwner().getId(), staffNotif.getRecipient().getId());
        assertEquals(NotificationType.NEW_ORDER_RECEIVED, staffNotif.getType());
        assertEquals("NEW_ORDER:" + orderId, staffNotif.getDedupKey());

        Notification customerNotif = captor.getAllValues().get(1);
        assertEquals(customerUser.getId(), customerNotif.getRecipient().getId());
        assertEquals(NotificationType.ORDER_STATUS_CHANGED, customerNotif.getType());
        assertNull(customerNotif.getDedupKey());
        assertTrue(customerNotif.getMessage().contains("EN_PREPARACION"));
    }

    @Test
    @DisplayName("markAsRead, markAllAsRead, getMyUnreadCount y getMyNotifications funcionan correctamente")
    void readAndQueryOperationsWork() {
        UUID notifId = UUID.randomUUID();
        Notification unread = Notification.builder()
                .id(notifId)
                .recipient(recipient)
                .tenant(tenant)
                .type(NotificationType.STAFF_INVITATION)
                .title("Invitación")
                .message("Únete")
                .actionRequired(true)
                .actionStatus(NotificationActionStatus.PENDING)
                .isRead(false)
                .build();

        when(currentUserProvider.getCurrentUserId()).thenReturn(recipientId);
        when(notificationRepository.countByRecipientIdAndIsReadFalse(recipientId)).thenReturn(3L);

        UnreadNotificationCountResponse countResponse = notificationService.getMyUnreadCount();
        assertEquals(3L, countResponse.unreadCount());

        Pageable pageable = PageRequest.of(0, 10);
        when(notificationRepository.findAllByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(recipientId, pageable))
                .thenReturn(new PageImpl<>(List.of(unread), pageable, 1));

        PageResponse<NotificationResponse> page = notificationService.getMyNotifications(true, null, pageable);
        assertEquals(1, page.content().size());
        assertEquals(List.of("ACCEPT", "REJECT"), page.content().get(0).allowedActions());

        when(notificationRepository.findByIdAndRecipientId(notifId, recipientId)).thenReturn(Optional.of(unread));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse readResponse = notificationService.markAsRead(notifId);
        assertTrue(readResponse.isRead());
        assertNotNull(readResponse.readAt());

        Notification unread2 = Notification.builder()
                .id(UUID.randomUUID())
                .recipient(recipient)
                .type(NotificationType.LOW_STOCK)
                .title("Stock")
                .message("Bajo")
                .isRead(false)
                .build();
        when(notificationRepository.findAllByRecipientIdAndIsReadFalse(recipientId)).thenReturn(List.of(unread2));

        int updated = notificationService.markAllAsRead();
        assertEquals(1, updated);
        assertTrue(unread2.getIsRead());
        assertNotNull(unread2.getReadAt());
    }

    @Test
    @DisplayName("markAsRead lanza ResourceNotFoundException si la notificación no pertenece al usuario")
    void markAsReadThrowsNotFoundWhenNotOwned() {
        UUID notifId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(recipientId);
        when(notificationRepository.findByIdAndRecipientId(notifId, recipientId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> notificationService.markAsRead(notifId));
    }

    @Test
    @DisplayName("syncInvitationNotificationStatus sincroniza el estado si se respondió desde otro endpoint")
    void syncInvitationNotificationStatusUpdatesNotification() {
        UUID invitationId = UUID.randomUUID();
        Notification invitationNotif = Notification.builder()
                .id(UUID.randomUUID())
                .recipient(recipient)
                .tenant(tenant)
                .type(NotificationType.STAFF_INVITATION)
                .title("Invitación")
                .message("Mensaje")
                .actionRequired(true)
                .actionStatus(NotificationActionStatus.PENDING)
                .referenceId(invitationId)
                .isRead(false)
                .build();

        when(notificationRepository.findByReferenceIdAndType(invitationId, NotificationType.STAFF_INVITATION))
                .thenReturn(Optional.of(invitationNotif));

        notificationService.syncInvitationNotificationStatus(invitationId, NotificationActionStatus.ACCEPTED);

        assertEquals(NotificationActionStatus.ACCEPTED, invitationNotif.getActionStatus());
        assertTrue(invitationNotif.getIsRead());
        assertNotNull(invitationNotif.getReadAt());
        assertNotNull(invitationNotif.getRespondedAt());
        verify(notificationRepository).save(invitationNotif);
    }
}
