package lat.nexofood.api.modules.tracking.infrastructure.repository;

import lat.nexofood.api.modules.tracking.domain.DeliveryLocationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryLocationHistoryRepository extends JpaRepository<DeliveryLocationHistory, UUID> {

    List<DeliveryLocationHistory> findAllByOrderIdAndTenantIdOrderByRecordedAtAsc(UUID orderId, UUID tenantId);

    @Modifying
    @Query("DELETE FROM DeliveryLocationHistory h WHERE h.recordedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") OffsetDateTime cutoff);
}

