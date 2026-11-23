package lat.nexofood.api.modules.notification.web.dto.request;

import jakarta.validation.constraints.NotNull;

public record NotificationInvitationDecisionRequest(
        @NotNull(message = "La decisión (ACCEPT o REJECT) es obligatoria")
        InvitationActionType decision
) {
}
