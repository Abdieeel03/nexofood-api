package lat.nexofood.api.modules.notification.application.service;

import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.notification.domain.NotificationActionStatus;
import lat.nexofood.api.modules.notification.domain.NotificationType;
import lat.nexofood.api.modules.notification.web.dto.request.NotificationInvitationDecisionRequest;
import lat.nexofood.api.modules.notification.web.dto.response.NotificationResponse;
import lat.nexofood.api.modules.notification.web.dto.response.UnreadNotificationCountResponse;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface NotificationService {

    NotificationResponse createNotification(
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
    );

    void notifyTenantStaff(
            Tenant tenant,
            Collection<TenantStaffRole> roles,
            NotificationType type,
            String title,
            String message,
            UUID referenceId,
            String dedupKey,
            Map<String, Object> metadata
    );

    PageResponse<NotificationResponse> getMyNotifications(
            Boolean unreadOnly,
            NotificationType type,
            Pageable pageable
    );

    UnreadNotificationCountResponse getMyUnreadCount();

    NotificationResponse markAsRead(UUID notificationId);

    int markAllAsRead();

    NotificationResponse respondToInvitation(
            UUID notificationId,
            NotificationInvitationDecisionRequest request
    );

    void syncInvitationNotificationStatus(UUID invitationId, NotificationActionStatus status);

    void checkAndNotifyProductStock(Product product);

    void notifyNewOrder(Order order);

    void notifyOrderStatusChanged(Order order);
}
