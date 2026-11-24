package lat.nexofood.api.modules.notification.web.dto.response;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record NotificationStreamTicketResponse(
        String ticket,
        long expiresInSeconds,
        OffsetDateTime expiresAt
) {
}
