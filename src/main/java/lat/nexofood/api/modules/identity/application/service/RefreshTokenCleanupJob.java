package lat.nexofood.api.modules.identity.application.service;

import lat.nexofood.api.modules.identity.infrastructure.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Job programado para eliminar periódicamente los refresh tokens expirados o revocados,
 * manteniendo limpia la tabla {@code refresh_tokens}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "${app.jwt.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public int purgeExpiredAndRevokedTokens() {
        int deleted = refreshTokenRepository.deleteExpiredOrRevokedTokens(LocalDateTime.now());
        log.info("Limpieza programada de refresh tokens completada. Registros eliminados: {}", deleted);
        return deleted;
    }
}
