package lat.nexofood.api.modules.notification.application.service;

import jakarta.annotation.PreDestroy;
import lat.nexofood.api.modules.notification.web.dto.response.NotificationResponse;
import lat.nexofood.api.modules.notification.web.dto.response.NotificationSseConnectedEvent;
import lat.nexofood.api.modules.notification.web.dto.response.UnreadNotificationCountResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class NotificationSseService {

    public static final String EVENT_CONNECTED = "connected";
    public static final String EVENT_NOTIFICATION = "notification";
    public static final String EVENT_UNREAD_COUNT = "unread-count";
    public static final long DEFAULT_RECONNECT_TIME_MS = 3000L;

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    @Value("${app.notifications.sse.timeout-ms:1800000}")
    private long emitterTimeoutMs = 1_800_000L;

    @Value("${app.notifications.sse.max-connections-per-user:5}")
    private int maxConnectionsPerUser = 5;

    public SseEmitter subscribe(UUID userId, long initialUnreadCount) {
        SseEmitter emitter = new SseEmitter(emitterTimeoutMs);

        CopyOnWriteArrayList<SseEmitter> userEmitters =
                emittersByUser.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>());

        int maxAllowed = Math.max(1, maxConnectionsPerUser);
        while (userEmitters.size() >= maxAllowed) {
            SseEmitter oldest = userEmitters.remove(0);
            completeSilently(oldest);
        }

        userEmitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> {
            removeEmitter(userId, emitter);
            completeSilently(emitter);
        });
        emitter.onError(ex -> {
            log.debug("Error en conexión SSE para usuario {}: {}", userId, ex.getMessage());
            removeEmitter(userId, emitter);
            completeSilently(emitter);
        });

        NotificationSseConnectedEvent connectedEvent = NotificationSseConnectedEvent.builder()
                .status("CONNECTED")
                .unreadCount(initialUnreadCount)
                .connectedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        try {
            emitter.send(SseEmitter.event()
                    .name(EVENT_CONNECTED)
                    .reconnectTime(DEFAULT_RECONNECT_TIME_MS)
                    .data(connectedEvent, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            log.debug("No se pudo enviar evento inicial SSE al usuario {}: {}", userId, ex.getMessage());
            removeEmitter(userId, emitter);
            completeSilently(emitter);
        }

        return emitter;
    }

    public boolean hasActiveConnections(UUID userId) {
        if (userId == null) {
            return false;
        }
        List<SseEmitter> emitters = emittersByUser.get(userId);
        return emitters != null && !emitters.isEmpty();
    }

    public void sendNotification(UUID userId, NotificationResponse notification, long unreadCount) {
        if (userId == null || notification == null) {
            return;
        }
        CopyOnWriteArrayList<SseEmitter> userEmitters = emittersByUser.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        UnreadNotificationCountResponse unreadPayload = UnreadNotificationCountResponse.builder()
                .unreadCount(unreadCount)
                .build();

        for (SseEmitter emitter : userEmitters) {
            try {
                SseEmitter.SseEventBuilder notifBuilder = SseEmitter.event()
                        .name(EVENT_NOTIFICATION)
                        .data(notification, MediaType.APPLICATION_JSON);
                if (notification.id() != null) {
                    notifBuilder.id(notification.id().toString());
                }
                emitter.send(notifBuilder);

                emitter.send(SseEmitter.event()
                        .name(EVENT_UNREAD_COUNT)
                        .data(unreadPayload, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException ex) {
                log.debug("Descartando conexión SSE cerrada para usuario {}: {}", userId, ex.getMessage());
                removeEmitter(userId, emitter);
                completeSilently(emitter);
            }
        }
    }

    public void sendUnreadCount(UUID userId, long unreadCount) {
        if (userId == null) {
            return;
        }
        CopyOnWriteArrayList<SseEmitter> userEmitters = emittersByUser.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        UnreadNotificationCountResponse unreadPayload = UnreadNotificationCountResponse.builder()
                .unreadCount(unreadCount)
                .build();

        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(EVENT_UNREAD_COUNT)
                        .data(unreadPayload, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException ex) {
                log.debug("Descartando conexión SSE cerrada para usuario {}: {}", userId, ex.getMessage());
                removeEmitter(userId, emitter);
                completeSilently(emitter);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.notifications.sse.heartbeat-interval-ms:25000}")
    public void sendHeartbeat() {
        for (Map.Entry<UUID, CopyOnWriteArrayList<SseEmitter>> entry : emittersByUser.entrySet()) {
            UUID userId = entry.getKey();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (IOException | IllegalStateException ex) {
                    log.debug("Heartbeat fallido; removiendo conexión SSE para usuario {}", userId);
                    removeEmitter(userId, emitter);
                    completeSilently(emitter);
                }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        emittersByUser.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                completeSilently(emitter);
            }
        });
        emittersByUser.clear();
    }

    public int getActiveConnectionCount(UUID userId) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        return emitters == null ? 0 : emitters.size();
    }

    public int getTotalActiveConnections() {
        return emittersByUser.values().stream().mapToInt(List::size).sum();
    }

    public void setEmitterTimeoutMs(long emitterTimeoutMs) {
        this.emitterTimeoutMs = emitterTimeoutMs;
    }

    public void setMaxConnectionsPerUser(int maxConnectionsPerUser) {
        this.maxConnectionsPerUser = maxConnectionsPerUser;
    }

    private void removeEmitter(UUID userId, SseEmitter emitter) {
        emittersByUser.computeIfPresent(userId, (key, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    private void completeSilently(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // La conexión ya estaba cerrada o completada
        }
    }
}
