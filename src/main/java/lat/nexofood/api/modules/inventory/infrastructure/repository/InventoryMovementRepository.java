package lat.nexofood.api.modules.inventory.infrastructure.repository;

import lat.nexofood.api.modules.inventory.domain.InventoryMovement;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementReason;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

    Optional<InventoryMovement> findByIdAndTenantId(UUID id, UUID tenantId);

    List<InventoryMovement> findAllByTenantId(UUID tenantId);

    Page<InventoryMovement> findAllByTenantId(UUID tenantId, Pageable pageable);

    List<InventoryMovement> findAllByTenantIdAndInventoryId(UUID tenantId, UUID inventoryId);

    Page<InventoryMovement> findAllByTenantIdAndInventoryId(UUID tenantId, UUID inventoryId, Pageable pageable);

    List<InventoryMovement> findAllByTenantIdAndItemId(UUID tenantId, UUID itemId);

    Page<InventoryMovement> findAllByTenantIdAndItemId(UUID tenantId, UUID itemId, Pageable pageable);

    List<InventoryMovement> findAllByTenantIdAndMovementType(UUID tenantId, InventoryMovementType movementType);

    Page<InventoryMovement> findAllByTenantIdAndMovementType(UUID tenantId, InventoryMovementType movementType, Pageable pageable);

    List<InventoryMovement> findAllByTenantIdAndReason(UUID tenantId, InventoryMovementReason reason);

    Page<InventoryMovement> findAllByTenantIdAndReason(UUID tenantId, InventoryMovementReason reason, Pageable pageable);

    List<InventoryMovement> findAllByTenantIdAndPerformedById(UUID tenantId, UUID performedById);

    Page<InventoryMovement> findAllByTenantIdAndPerformedById(UUID tenantId, UUID performedById, Pageable pageable);

    List<InventoryMovement> findAllByTenantIdAndCreatedAtBetween(UUID tenantId, OffsetDateTime startDate, OffsetDateTime endDate);

    Page<InventoryMovement> findAllByTenantIdAndCreatedAtBetween(UUID tenantId, OffsetDateTime startDate, OffsetDateTime endDate, Pageable pageable);

    @Query("SELECT m FROM InventoryMovement m WHERE m.tenant.id = :tenantId AND m.movementType = :movementType ORDER BY m.createdAt DESC")
    List<InventoryMovement> findByTenantIdAndMovementTypeOrderByCreatedAtDesc(
            @Param("tenantId") UUID tenantId,
            @Param("movementType") InventoryMovementType movementType
    );

    @Query("SELECT m FROM InventoryMovement m WHERE m.tenant.id = :tenantId AND m.movementType = :movementType ORDER BY m.createdAt DESC")
    Page<InventoryMovement> findByTenantIdAndMovementTypeOrderByCreatedAtDesc(
            @Param("tenantId") UUID tenantId,
            @Param("movementType") InventoryMovementType movementType,
            Pageable pageable
    );

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndMovementType(UUID tenantId, InventoryMovementType movementType);
}
