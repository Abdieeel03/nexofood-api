package lat.nexofood.api.modules.notification.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.notification.application.service.NotificationService;
import lat.nexofood.api.modules.notification.domain.NotificationType;
import lat.nexofood.api.modules.notification.web.dto.request.InvitationActionType;
import lat.nexofood.api.modules.notification.web.dto.request.NotificationInvitationDecisionRequest;
import lat.nexofood.api.modules.notification.web.dto.response.NotificationResponse;
import lat.nexofood.api.modules.notification.web.dto.response.UnreadNotificationCountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Gestión de notificaciones e invitaciones del usuario")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Listar mis notificaciones (con filtro opcional por no leídas y tipo)")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getMyNotifications(
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(required = false) NotificationType type,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<NotificationResponse> response = notificationService.getMyNotifications(unreadOnly, type, pageable);
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<NotificationResponse>>builder()
                        .success(true)
                        .message("Notificaciones obtenidas exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Obtener cantidad de notificaciones no leídas")
    public ResponseEntity<ApiResponse<UnreadNotificationCountResponse>> getMyUnreadCount() {
        UnreadNotificationCountResponse response = notificationService.getMyUnreadCount();
        return ResponseEntity.ok(
                ApiResponse.<UnreadNotificationCountResponse>builder()
                        .success(true)
                        .message("Contador de notificaciones obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marcar una notificación como leída")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(@PathVariable UUID id) {
        NotificationResponse response = notificationService.markAsRead(id);
        return ResponseEntity.ok(
                ApiResponse.<NotificationResponse>builder()
                        .success(true)
                        .message("Notificación marcada como leída")
                        .data(response)
                        .build()
        );
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Marcar todas mis notificaciones como leídas")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead() {
        int updatedCount = notificationService.markAllAsRead();
        return ResponseEntity.ok(
                ApiResponse.<Map<String, Integer>>builder()
                        .success(true)
                        .message("Notificaciones marcadas como leídas")
                        .data(Map.of("updatedCount", updatedCount))
                        .build()
        );
    }

    @RequestMapping(value = "/{id}/respond", method = {RequestMethod.POST, RequestMethod.PATCH})
    @Operation(summary = "Responder una notificación de invitación (ACCEPT o REJECT)")
    public ResponseEntity<ApiResponse<NotificationResponse>> respondToInvitation(
            @PathVariable UUID id,
            @Valid @RequestBody NotificationInvitationDecisionRequest request
    ) {
        NotificationResponse response = notificationService.respondToInvitation(id, request);
        String message = request.decision() == InvitationActionType.ACCEPT
                ? "Invitación aceptada exitosamente"
                : "Invitación rechazada exitosamente";
        return ResponseEntity.ok(
                ApiResponse.<NotificationResponse>builder()
                        .success(true)
                        .message(message)
                        .data(response)
                        .build()
        );
    }
}
