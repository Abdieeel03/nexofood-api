package lat.nexofood.api.modules.tracking.infrastructure.repository;

import lat.nexofood.api.modules.tracking.domain.DeliveryTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryTrackingRepository extends JpaRepository<DeliveryTracking, UUID> {

    Optional<DeliveryTracking> findByOrderIdAndTenantId(UUID orderId, UUID tenantId);

    List<DeliveryTracking> findAllByTenantIdAndIsActiveTrueOrderByUpdatedAtDesc(UUID tenantId);

    @Query(
            value = "SELECT ST_DistanceSphere(ST_SetSRID(ST_MakePoint(:lon1, :lat1), 4326), ST_SetSRID(ST_MakePoint(:lon2, :lat2), 4326)) / 1000.0",
            nativeQuery = true
    )
    Double calculateDistanceSphereKm(
            @Param("lat1") double lat1,
            @Param("lon1") double lon1,
            @Param("lat2") double lat2,
            @Param("lon2") double lon2
    );
}

