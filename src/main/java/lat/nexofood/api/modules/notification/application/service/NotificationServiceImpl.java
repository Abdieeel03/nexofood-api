package lat.nexofood.api.modules.notification.application.service;

import lat.nexofood.api.common.config.TenantConnectionPreparer;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.exception.UnauthorizedException;
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
import lat.nexofood.api.modules.notification.web.dto.response.NotificationStreamTicketResponse;
import lat.nexofood.api.modules.notification.web.dto.response.UnreadNotificationCountResponse;
import lat.nexofood.api.modules.notification.web.mapper.NotificationMapper;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantMember;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantMemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DEDUP_KEY_LENGTH = 150;
    private static final List<TenantStaffRole> OPERATIONS_ROLES = List.of(
            TenantStaffRole.OWNER,
            TenantStaffRole.ADMIN,
            TenantStaffRole.COCINERO
    );

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final TenantMemberRepository tenantMemberRepository;
    private final CurrentUserProvider currentUserProvider;
    private final TenantConnectionPreparer tenantConnectionPreparer;
    private final ObjectProvider<NotificationInvitationResponder> invitationResponderProvider;
    private final NotificationSseService notificationSseService;
    private final NotificationSseTicketService notificationSseTicketService;

    @Value("${app.notifications.low-stock-threshold:5}")
    private int lowStockThreshold = 5;

    @Autowired
    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            NotificationMapper notificationMapper,
            TenantMemberRepository tenantMemberRepository,
            CurrentUserProvider currentUserProvider,
            @Autowired(required = false) TenantConnectionPreparer tenantConnectionPreparer,
            ObjectProvider<NotificationInvitationResponder> invitationResponderProvider,
            @Autowired(required = false) NotificationSseService notificationSseService,
            @Autowired(required = false) NotificationSseTicketService notificationSseTicketService
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
        this.tenantMemberRepository = tenantMemberRepository;
        this.currentUserProvider = currentUserProvider;
        this.tenantConnectionPreparer = tenantConnectionPreparer;
        this.invitationResponderProvider = invitationResponderProvider;
        this.notificationSseService = notificationSseService;
        this.notificationSseTicketService = notificationSseTicketService;
    }

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            NotificationMapper notificationMapper,
            TenantMemberRepository tenantMemberRepository,
            CurrentUserProvider currentUserProvider,
            TenantConnectionPreparer tenantConnectionPreparer,
            ObjectProvider<NotificationInvitationResponder> invitationResponderProvider
    ) {
        this(
                notificationRepository,
                notificationMapper,
                tenantMemberRepository,
                currentUserProvider,
                tenantConnectionPreparer,
                invitationResponderProvider,
                null,
                null
        );
    }

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            NotificationMapper notificationMapper,
            TenantMemberRepository tenantMemberRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this(notificationRepository, notificationMapper, tenantMemberRepository, currentUserProvider, null, null, null, null);
    }

    @Override
    @Transactional
    public NotificationResponse createNotification(
            User recipient,
            Tenant tenant,
            NotificationType type,
            String title,
            String message,
            boolean actionRequired,
            NotificationActionStatus actionStatus,
            UUID referenceId,
            String dedupKey,
            Map<String, Object> metadata
    ) {
        if (recipient == null || recipient.getId() == null) {
            log.warn("No se pudo crear la notificación de tipo {} porque el destinatario es nulo", type);
            return null;
        }

        String normalizedDedupKey = normalizeDedupKey(dedupKey);
        if (normalizedDedupKey != null
                && notificationRepository.existsByRecipientIdAndDedupKey(recipient.getId(), normalizedDedupKey)) {
            log.debug("Notificación duplicada omitida para usuario {} con dedupKey {}",
                    recipient.getId(), normalizedDedupKey);
            return null;
        }

        NotificationActionStatus resolvedActionStatus = actionStatus != null
                ? actionStatus
                : (actionRequired ? NotificationActionStatus.PENDING : NotificationActionStatus.NONE);

        Notification notification = Notification.builder()
                .recipient(recipient)
                .tenant(tenant)
                .type(type)
                .title(truncate(title, MAX_TITLE_LENGTH))
                .message(message)
                .actionRequired(actionRequired)
                .actionStatus(resolvedActionStatus)
                .referenceId(referenceId)
                .dedupKey(normalizedDedupKey)
                .metadata(metadata)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        NotificationResponse response = notificationMapper.toResponse(saved);
        publishNewNotificationAfterCommit(recipient.getId(), response);
        return response;
    }

    @Override
    @Transactional
    public void notifyTenantStaff(
            Tenant tenant,
            Collection<TenantStaffRole> roles,
            NotificationType type,
            String title,
            String message,
            UUID referenceId,
            String dedupKey,
            Map<String, Object> metadata
    ) {
        if (tenant == null || tenant.getId() == null || roles == null || roles.isEmpty()) {
            return;
        }

        Map<UUID, User> recipients = new LinkedHashMap<>();

        if (roles.contains(TenantStaffRole.OWNER) && isEligibleRecipient(tenant.getOwner())) {
            recipients.put(tenant.getOwner().getId(), tenant.getOwner());
        }

        if (tenantConnectionPreparer != null) {
            tenantConnectionPreparer.setTenantContext(tenant.getId());
        }

        List<TenantMember> members = tenantMemberRepository.findAllByTenantId(tenant.getId());
        for (TenantMember member : members) {
            if (!Boolean.TRUE.equals(member.getIsActive())) {
                continue;
            }
            if (member.getRole() == null || !roles.contains(member.getRole())) {
                continue;
            }
            User memberUser = member.getUser();
            if (isEligibleRecipient(memberUser)) {
                recipients.putIfAbsent(memberUser.getId(), memberUser);
            }
        }

        for (User recipient : recipients.values()) {
            createNotification(
                    recipient,
                    tenant,
                    type,
                    title,
                    message,
                    false,
                    NotificationActionStatus.NONE,
                    referenceId,
                    dedupKey,
                    metadata
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getMyNotifications(
            Boolean unreadOnly,
            NotificationType type,
            Pageable pageable
    ) {
        UUID userId = currentUserProvider.getCurrentUserId();
        boolean onlyUnread = Boolean.TRUE.equals(unreadOnly);

        Page<Notification> page;
        if (onlyUnread && type != null) {
            page = notificationRepository.findAllByRecipientIdAndIsReadFalseAndTypeOrderByCreatedAtDesc(
                    userId, type, pageable);
        } else if (onlyUnread) {
            page = notificationRepository.findAllByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(
                    userId, pageable);
        } else if (type != null) {
            page = notificationRepository.findAllByRecipientIdAndTypeOrderByCreatedAtDesc(
                    userId, type, pageable);
        } else {
            page = notificationRepository.findAllByRecipientIdOrderByCreatedAtDesc(
                    userId, pageable);
        }

        return PageResponse.from(page.map(notificationMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse getMyUnreadCount() {
        UUID userId = currentUserProvider.getCurrentUserId();
        long count = notificationRepository.countByRecipientIdAndIsReadFalse(userId);
        return UnreadNotificationCountResponse.builder()
                .unreadCount(count)
                .build();
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(UUID notificationId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificación no encontrada"));

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(OffsetDateTime.now());
            notification = notificationRepository.save(notification);
            publishUnreadCountAfterCommit(userId);
        }

        return notificationMapper.toResponse(notification);
    }

    @Override
    @Transactional
    public int markAllAsRead() {
        UUID userId = currentUserProvider.getCurrentUserId();
        List<Notification> unreadNotifications = notificationRepository.findAllByRecipientIdAndIsReadFalse(userId);
        if (unreadNotifications.isEmpty()) {
            return 0;
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (Notification notification : unreadNotifications) {
            notification.setIsRead(true);
            if (notification.getReadAt() == null) {
                notification.setReadAt(now);
            }
        }
        notificationRepository.saveAll(unreadNotifications);
        publishUnreadCountAfterCommit(userId, 0L);
        return unreadNotifications.size();
    }

    @Override
    @Transactional
    public NotificationResponse respondToInvitation(
            UUID notificationId,
            NotificationInvitationDecisionRequest request
    ) {
        UUID userId = currentUserProvider.getCurrentUserId();
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificación no encontrada"));

        if (notification.getType() != NotificationType.STAFF_INVITATION
                || !Boolean.TRUE.equals(notification.getActionRequired())) {
            throw new BadRequestException("Esta notificación no requiere una decisión de invitación");
        }

        if (notification.getActionStatus() != NotificationActionStatus.PENDING) {
            throw new ResourceConflictException("Esta invitación ya fue respondida");
        }

        if (notification.getReferenceId() == null) {
            throw new BadRequestException("La notificación no tiene una invitación asociada");
        }

        NotificationInvitationResponder responder = invitationResponderProvider != null
                ? invitationResponderProvider.getIfAvailable()
                : null;
        if (responder != null) {
            responder.onInvitationDecision(notification.getReferenceId(), userId, request.decision());
        }

        OffsetDateTime now = OffsetDateTime.now();
        NotificationActionStatus nextStatus = request.decision() == InvitationActionType.ACCEPT
                ? NotificationActionStatus.ACCEPTED
                : NotificationActionStatus.REJECTED;

        notification.setActionStatus(nextStatus);
        notification.setIsRead(true);
        if (notification.getReadAt() == null) {
            notification.setReadAt(now);
        }
        notification.setRespondedAt(now);

        Notification saved = notificationRepository.save(notification);
        publishUnreadCountAfterCommit(userId);
        return notificationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void syncInvitationNotificationStatus(UUID invitationId, NotificationActionStatus status) {
        if (invitationId == null || status == null) {
            return;
        }
        notificationRepository.findByReferenceIdAndType(invitationId, NotificationType.STAFF_INVITATION)
                .ifPresent(notification -> {
                    OffsetDateTime now = OffsetDateTime.now();
                    notification.setActionStatus(status);
                    notification.setIsRead(true);
                    if (notification.getReadAt() == null) {
                        notification.setReadAt(now);
                    }
                    if (notification.getRespondedAt() == null) {
                        notification.setRespondedAt(now);
                    }
                    notificationRepository.save(notification);
                    if (notification.getRecipient() != null && notification.getRecipient().getId() != null) {
                        publishUnreadCountAfterCommit(notification.getRecipient().getId());
                    }
                });
    }

    @Override
    @Transactional
    public void checkAndNotifyProductStock(Product product) {
        if (product == null || product.getTenant() == null || product.getStock() == null) {
            return;
        }

        int stock = product.getStock();
        if (stock > lowStockThreshold) {
            return;
        }

        LocalDate today = LocalDate.now();
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (product.getId() != null) {
            metadata.put("productId", product.getId().toString());
        }
        metadata.put("productName", product.getName());
        metadata.put("stock", stock);
        metadata.put("threshold", lowStockThreshold);

        if (stock == 0) {
            String dedupKey = "OUT_OF_STOCK:" + product.getId() + ":" + today;
            String title = "Producto sin stock: " + product.getName();
            String message = "El producto '" + product.getName() + "' en '" + product.getTenant().getName()
                    + "' se quedó sin stock (0 unidades).";
            notifyTenantStaff(
                    product.getTenant(),
                    OPERATIONS_ROLES,
                    NotificationType.OUT_OF_STOCK,
                    title,
                    message,
                    product.getId(),
                    dedupKey,
                    metadata
            );
        } else {
            String dedupKey = "LOW_STOCK:" + product.getId() + ":" + today + ":" + stock;
            String title = "Stock bajo: " + product.getName();
            String message = "El producto '" + product.getName() + "' en '" + product.getTenant().getName()
                    + "' tiene stock bajo (" + stock + " unidades restantes).";
            notifyTenantStaff(
                    product.getTenant(),
                    OPERATIONS_ROLES,
                    NotificationType.LOW_STOCK,
                    title,
                    message,
                    product.getId(),
                    dedupKey,
                    metadata
            );
        }
    }

    @Override
    @Transactional
    public void notifyNewOrder(Order order) {
        if (order == null || order.getTenant() == null) {
            return;
        }

        String orderRef = order.getOrderNumber() != null ? order.getOrderNumber() : String.valueOf(order.getId());
        String title = "Nuevo pedido recibido: #" + orderRef;
        StringBuilder message = new StringBuilder("Se ha recibido el pedido #")
                .append(orderRef)
                .append(" en '")
                .append(order.getTenant().getName())
                .append("'");
        if (order.getTotal() != null) {
            message.append(" por S/ ").append(order.getTotal());
        }
        message.append(".");

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (order.getId() != null) {
            metadata.put("orderId", order.getId().toString());
        }
        if (order.getOrderNumber() != null) {
            metadata.put("orderNumber", order.getOrderNumber());
        }
        if (order.getStatus() != null) {
            metadata.put("status", order.getStatus().name());
        }
        if (order.getDeliveryType() != null) {
            metadata.put("deliveryType", order.getDeliveryType().name());
        }
        if (order.getTotal() != null) {
            metadata.put("total", order.getTotal());
        }

        String dedupKey = order.getId() != null ? "NEW_ORDER:" + order.getId() : null;

        notifyTenantStaff(
                order.getTenant(),
                OPERATIONS_ROLES,
                NotificationType.NEW_ORDER_RECEIVED,
                title,
                message.toString(),
                order.getId(),
                dedupKey,
                metadata
        );
    }

    @Override
    @Transactional
    public void notifyOrderStatusChanged(Order order) {
        if (order == null || order.getCustomer() == null || order.getCustomer().getUser() == null) {
            return;
        }

        User customerUser = order.getCustomer().getUser();
        String orderRef = order.getOrderNumber() != null ? order.getOrderNumber() : String.valueOf(order.getId());
        String statusName = order.getStatus() != null ? order.getStatus().name() : "";
        String storeName = order.getTenant() != null ? order.getTenant().getName() : "el restaurante";

        String title = "Actualización de tu pedido #" + orderRef;
        String message = "Tu pedido #" + orderRef + " en '" + storeName + "' cambió al estado: " + statusName + ".";

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (order.getId() != null) {
            metadata.put("orderId", order.getId().toString());
        }
        if (order.getOrderNumber() != null) {
            metadata.put("orderNumber", order.getOrderNumber());
        }
        if (order.getStatus() != null) {
            metadata.put("status", order.getStatus().name());
        }

        createNotification(
                customerUser,
                order.getTenant(),
                NotificationType.ORDER_STATUS_CHANGED,
                title,
                message,
                false,
                NotificationActionStatus.NONE,
                order.getId(),
                null,
                metadata
        );
    }

    @Override
    public NotificationStreamTicketResponse issueStreamTicket() {
        if (notificationSseTicketService == null) {
            throw new IllegalStateException("El servicio de tickets SSE no está disponible");
        }
        UUID userId = currentUserProvider.getCurrentUserId();
        return notificationSseTicketService.issueTicket(userId);
    }

    @Override
    public SseEmitter subscribeToStream(String ticket) {
        if (notificationSseService == null) {
            throw new IllegalStateException("El servicio SSE de notificaciones no está disponible");
        }

        UUID userId;
        if (ticket != null && !ticket.isBlank()) {
            if (notificationSseTicketService == null) {
                throw new UnauthorizedException("Ticket SSE inválido, expirado o ya utilizado");
            }
            userId = notificationSseTicketService.consumeTicket(ticket)
                    .orElseThrow(() -> new UnauthorizedException("Ticket SSE inválido, expirado o ya utilizado"));
        } else if (currentUserProvider.getCurrentEmailOptional().isPresent()) {
            userId = currentUserProvider.getCurrentUserId();
        } else {
            throw new UnauthorizedException("No autorizado: token inválido, expirado o ausente");
        }

        long unreadCount = notificationRepository.countByRecipientIdAndIsReadFalse(userId);
        return notificationSseService.subscribe(userId, unreadCount);
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    private boolean isEligibleRecipient(User user) {
        return user != null
                && user.getId() != null
                && user.getDeletedAt() == null
                && !Boolean.FALSE.equals(user.getIsActive());
    }

    private String normalizeDedupKey(String dedupKey) {
        if (dedupKey == null || dedupKey.isBlank()) {
            return null;
        }
        return truncate(dedupKey.trim(), MAX_DEDUP_KEY_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void publishNewNotificationAfterCommit(UUID userId, NotificationResponse response) {
        if (notificationSseService == null || userId == null || response == null
                || !notificationSseService.hasActiveConnections(userId)) {
            return;
        }
        long unreadCount = notificationRepository.countByRecipientIdAndIsReadFalse(userId);
        runAfterCommit(() -> notificationSseService.sendNotification(userId, response, unreadCount));
    }

    private void publishUnreadCountAfterCommit(UUID userId) {
        if (notificationSseService == null || userId == null
                || !notificationSseService.hasActiveConnections(userId)) {
            return;
        }
        long unreadCount = notificationRepository.countByRecipientIdAndIsReadFalse(userId);
        publishUnreadCountAfterCommit(userId, unreadCount);
    }

    private void publishUnreadCountAfterCommit(UUID userId, long unreadCount) {
        if (notificationSseService == null || userId == null
                || !notificationSseService.hasActiveConnections(userId)) {
            return;
        }
        runAfterCommit(() -> notificationSseService.sendUnreadCount(userId, unreadCount));
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
