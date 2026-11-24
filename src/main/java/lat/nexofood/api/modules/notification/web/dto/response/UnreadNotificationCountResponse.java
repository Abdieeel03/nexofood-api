package lat.nexofood.api.modules.notification.web.dto.response;

import lombok.Builder;

@Builder
public record UnreadNotificationCountResponse(
        long unreadCount
) {
}
