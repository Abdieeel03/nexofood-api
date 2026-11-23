package lat.nexofood.api.modules.notification.web.mapper;

import lat.nexofood.api.modules.notification.domain.Notification;
import lat.nexofood.api.modules.notification.domain.NotificationActionStatus;
import lat.nexofood.api.modules.notification.web.dto.response.NotificationResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        if (notification == null) {
            return null;
        }

        List<String> allowedActions = (Boolean.TRUE.equals(notification.getActionRequired())
                && notification.getActionStatus() == NotificationActionStatus.PENDING)
                ? List.of("ACCEPT", "REJECT")
                : List.of();

        return NotificationResponse.builder()
                .id(notification.getId())
                .recipientUserId(notification.getRecipient() != null ? notification.getRecipient().getId() : null)
                .tenantId(notification.getTenant() != null ? notification.getTenant().getId() : null)
                .tenantName(notification.getTenant() != null ? notification.getTenant().getName() : null)
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .actionRequired(notification.getActionRequired())
                .actionStatus(notification.getActionStatus())
                .allowedActions(allowedActions)
                .referenceId(notification.getReferenceId())
                .metadata(notification.getMetadata())
                .isRead(notification.getIsRead())
                .readAt(notification.getReadAt())
                .respondedAt(notification.getRespondedAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
