package lat.nexofood.api.modules.notification.web.dto.response;

import lat.nexofood.api.modules.notification.domain.NotificationActionStatus;
import lat.nexofood.api.modules.notification.domain.NotificationType;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record NotificationResponse(
        UUID id,
        UUID recipientUserId,
        UUID tenantId,
        String tenantName,
        NotificationType type,
        String title,
        String message,
        Boolean actionRequired,
        NotificationActionStatus actionStatus,
        List<String> allowedActions,
        UUID referenceId,
        Map<String, Object> metadata,
        Boolean isRead,
        OffsetDateTime readAt,
        OffsetDateTime respondedAt,
        OffsetDateTime createdAt
) {
}
