package lat.nexofood.api.modules.tracking.application.scheduler;

import lat.nexofood.api.modules.tracking.application.config.TrackingProperties;
import lat.nexofood.api.modules.tracking.infrastructure.repository.DeliveryLocationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrackingHistoryCleanupJob {

    private final DeliveryLocationHistoryRepository historyRepository;
    private final TrackingProperties properties;

    @Scheduled(cron = "${app.tracking.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public int purgeOldTrackingHistory() {
        int retentionDays = Math.max(1, properties.getHistoryRetentionDays());
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(retentionDays);
        int deleted = historyRepository.deleteOlderThan(cutoff);
        log.info("Limpieza programada de historial de ubicaciones GPS completada (> {} días). Registros eliminados: {}",
                retentionDays, deleted);
        return deleted;
    }
}

