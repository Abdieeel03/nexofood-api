package lat.nexofood.api.modules.notification.web.dto.response;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record NotificationSseConnectedEvent(
        String status,
        long unreadCount,
        OffsetDateTime connectedAt
) {
}
