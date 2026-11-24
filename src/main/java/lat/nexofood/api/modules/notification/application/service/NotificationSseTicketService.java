package lat.nexofood.api.modules.notification.application.service;

import lat.nexofood.api.modules.notification.web.dto.response.NotificationStreamTicketResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class NotificationSseTicketService {

    private static final int TICKET_BYTE_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, TicketEntry> tickets = new ConcurrentHashMap<>();

    @Value("${app.notifications.sse.ticket-ttl-seconds:30}")
    private long ticketTtlSeconds = 30L;

    public NotificationStreamTicketResponse issueTicket(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("El ID de usuario es obligatorio para emitir un ticket SSE");
        }

        long effectiveTtl = Math.max(5L, ticketTtlSeconds);
        Instant expiresAt = Instant.now().plusSeconds(effectiveTtl);
        String token = generateToken();

        tickets.put(token, new TicketEntry(userId, expiresAt));

        return NotificationStreamTicketResponse.builder()
                .ticket(token)
                .expiresInSeconds(effectiveTtl)
                .expiresAt(OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
                .build();
    }

    public Optional<UUID> consumeTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }

        TicketEntry entry = tickets.remove(ticket.trim());
        if (entry == null) {
            return Optional.empty();
        }

        if (!entry.expiresAt().isAfter(Instant.now())) {
            log.debug("Ticket SSE expirado descartado para usuario {}", entry.userId());
            return Optional.empty();
        }

        return Optional.of(entry.userId());
    }

    @Scheduled(fixedDelayString = "${app.notifications.sse.ticket-cleanup-interval-ms:60000}")
    public void purgeExpiredTickets() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    public long getTicketTtlSeconds() {
        return ticketTtlSeconds;
    }

    public void setTicketTtlSeconds(long ticketTtlSeconds) {
        this.ticketTtlSeconds = ticketTtlSeconds;
    }

    int getActiveTicketCount() {
        return tickets.size();
    }

    private String generateToken() {
        byte[] bytes = new byte[TICKET_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record TicketEntry(UUID userId, Instant expiresAt) {
    }
}
