package lat.nexofood.api.modules.inventory.infrastructure.repository;

import lat.nexofood.api.modules.inventory.domain.InventoryStock;
import lat.nexofood.api.modules.inventory.domain.InventoryUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryStockRepository extends JpaRepository<InventoryStock, UUID> {

    Optional<InventoryStock> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<InventoryStock> findByTenantIdAndItemId(UUID tenantId, UUID itemId);

    boolean existsByTenantIdAndItemId(UUID tenantId, UUID itemId);

    List<InventoryStock> findAllByTenantId(UUID tenantId);

    Page<InventoryStock> findAllByTenantId(UUID tenantId, Pageable pageable);

    List<InventoryStock> findAllByTenantIdAndIsActiveTrue(UUID tenantId);

    Page<InventoryStock> findAllByTenantIdAndIsActiveTrue(UUID tenantId, Pageable pageable);

    List<InventoryStock> findAllByTenantIdAndItemUnit(UUID tenantId, InventoryUnit unit);

    Page<InventoryStock> findAllByTenantIdAndItemUnit(UUID tenantId, InventoryUnit unit, Pageable pageable);

    @Query("SELECT s FROM InventoryStock s WHERE s.tenant.id = :tenantId AND s.isActive = true AND s.quantity <= s.minimumStock")
    List<InventoryStock> findLowStockByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT s FROM InventoryStock s WHERE s.tenant.id = :tenantId AND s.isActive = true AND s.quantity <= s.minimumStock")
    Page<InventoryStock> findLowStockByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndIsActiveTrue(UUID tenantId);

    @Query("SELECT COUNT(s) FROM InventoryStock s WHERE s.tenant.id = :tenantId AND s.isActive = true AND s.quantity <= s.minimumStock")
    long countLowStockByTenantId(@Param("tenantId") UUID tenantId);
}
