package lat.nexofood.api.modules.notification.web.controller;

import lat.nexofood.api.common.exception.GlobalExceptionHandler;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.UnauthorizedException;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.notification.application.service.NotificationService;
import lat.nexofood.api.modules.notification.domain.NotificationActionStatus;
import lat.nexofood.api.modules.notification.domain.NotificationType;
import lat.nexofood.api.modules.notification.web.dto.request.InvitationActionType;
import lat.nexofood.api.modules.notification.web.dto.request.NotificationInvitationDecisionRequest;
import lat.nexofood.api.modules.notification.web.dto.response.NotificationResponse;
import lat.nexofood.api.modules.notification.web.dto.response.NotificationStreamTicketResponse;
import lat.nexofood.api.modules.notification.web.dto.response.UnreadNotificationCountResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        NotificationController controller = new NotificationController(notificationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/notifications retorna lista paginada y expone allowedActions ACCEPT/REJECT")
    void getMyNotificationsReturns200() throws Exception {
        UUID notifId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID refId = UUID.randomUUID();

        NotificationResponse item = NotificationResponse.builder()
                .id(notifId)
                .recipientUserId(userId)
                .tenantId(tenantId)
                .tenantName("Burger Bros")
                .type(NotificationType.STAFF_INVITATION)
                .title("Invitación al equipo")
                .message("Te han invitado")
                .actionRequired(true)
                .actionStatus(NotificationActionStatus.PENDING)
                .allowedActions(List.of("ACCEPT", "REJECT"))
                .referenceId(refId)
                .metadata(Map.of("role", "COCINERO"))
                .isRead(false)
                .createdAt(OffsetDateTime.now())
                .build();

        PageResponse<NotificationResponse> page = PageResponse.<NotificationResponse>builder()
                .content(List.of(item))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(notificationService.getMyNotifications(eq(true), eq(NotificationType.STAFF_INVITATION), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/notifications")
                        .param("unreadOnly", "true")
                        .param("type", "STAFF_INVITATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(notifId.toString()))
                .andExpect(jsonPath("$.data.content[0].allowedActions[0]").value("ACCEPT"))
                .andExpect(jsonPath("$.data.content[0].allowedActions[1]").value("REJECT"));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/unread-count retorna contador de no leídas")
    void getUnreadCountReturns200() throws Exception {
        when(notificationService.getMyUnreadCount())
                .thenReturn(UnreadNotificationCountResponse.builder().unreadCount(5L).build());

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(5));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read y /read-all marcan como leídas (200 OK)")
    void markReadEndpointsReturn200() throws Exception {
        UUID notifId = UUID.randomUUID();
        when(notificationService.markAsRead(notifId)).thenReturn(
                NotificationResponse.builder()
                        .id(notifId)
                        .isRead(true)
                        .allowedActions(List.of())
                        .build()
        );
        when(notificationService.markAllAsRead()).thenReturn(4);

        mockMvc.perform(patch("/api/v1/notifications/" + notifId + "/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isRead").value(true));

        mockMvc.perform(patch("/api/v1/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.updatedCount").value(4));
    }

    @Test
    @DisplayName("POST /api/v1/notifications/{id}/respond rechaza decisiones nulas o inválidas con 400 Bad Request")
    void respondRejectsInvalidDecisionWith400() throws Exception {
        UUID notifId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/notifications/" + notifId + "/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/v1/notifications/" + notifId + "/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(notificationService, never()).respondToInvitation(any(), any());
    }

    @Test
    @DisplayName("POST y PATCH /api/v1/notifications/{id}/respond aceptan únicamente ACCEPT o REJECT y retornan 200 / 409")
    void respondSupportsPostAndPatchForAcceptAndReject() throws Exception {
        UUID notifId = UUID.randomUUID();

        when(notificationService.respondToInvitation(
                eq(notifId),
                eq(new NotificationInvitationDecisionRequest(InvitationActionType.ACCEPT))
        )).thenReturn(
                NotificationResponse.builder()
                        .id(notifId)
                        .type(NotificationType.STAFF_INVITATION)
                        .actionRequired(true)
                        .actionStatus(NotificationActionStatus.ACCEPTED)
                        .allowedActions(List.of())
                        .isRead(true)
                        .build()
        );

        mockMvc.perform(post("/api/v1/notifications/" + notifId + "/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.actionStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.allowedActions").isEmpty());

        when(notificationService.respondToInvitation(
                eq(notifId),
                eq(new NotificationInvitationDecisionRequest(InvitationActionType.REJECT))
        )).thenThrow(new ResourceConflictException("Esta invitación ya fue respondida"));

        mockMvc.perform(patch("/api/v1/notifications/" + notifId + "/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /api/v1/notifications/stream-ticket retorna ticket temporal SSE (200 OK)")
    void createStreamTicketReturns200() throws Exception {
        OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(30);
        when(notificationService.issueStreamTicket()).thenReturn(
                NotificationStreamTicketResponse.builder()
                        .ticket("opaque-ticket-123")
                        .expiresInSeconds(30L)
                        .expiresAt(expiresAt)
                        .build()
        );

        mockMvc.perform(post("/api/v1/notifications/stream-ticket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ticket").value("opaque-ticket-123"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(30));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/stream inicia stream SSE asíncrono con cabeceras anti-buffering")
    void streamNotificationsStartsAsyncSseStream() throws Exception {
        SseEmitter emitter = new SseEmitter(60_000L);
        when(notificationService.subscribeToStream("valid-ticket")).thenReturn(emitter);

        mockMvc.perform(get("/api/v1/notifications/stream")
                        .param("ticket", "valid-ticket")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Cache-Control", "no-cache, no-transform"))
                .andExpect(header().string("X-Accel-Buffering", "no"));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/stream retorna 401 JSON si el ticket o token es inválido")
    void streamNotificationsReturns401OnUnauthorized() throws Exception {
        when(notificationService.subscribeToStream("expired-ticket"))
                .thenThrow(new UnauthorizedException("Ticket SSE inválido, expirado o ya utilizado"));

        mockMvc.perform(get("/api/v1/notifications/stream")
                        .param("ticket", "expired-ticket")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }
}
