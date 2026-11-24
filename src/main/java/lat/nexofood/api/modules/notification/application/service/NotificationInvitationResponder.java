package lat.nexofood.api.modules.notification.application.service;

import lat.nexofood.api.modules.notification.web.dto.request.InvitationActionType;

import java.util.UUID;

public interface NotificationInvitationResponder {

    void onInvitationDecision(UUID invitationId, UUID recipientUserId, InvitationActionType decision);
}
