package lat.nexofood.api.modules.inventory.infrastructure.repository;

import lat.nexofood.api.modules.inventory.domain.Inventory;
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
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Inventory> findByTenantIdAndItemId(UUID tenantId, UUID itemId);

    boolean existsByTenantIdAndItemId(UUID tenantId, UUID itemId);

    List<Inventory> findAllByTenantId(UUID tenantId);

    Page<Inventory> findAllByTenantId(UUID tenantId, Pageable pageable);

    List<Inventory> findAllByTenantIdAndIsActiveTrue(UUID tenantId);

    Page<Inventory> findAllByTenantIdAndIsActiveTrue(UUID tenantId, Pageable pageable);

    List<Inventory> findAllByTenantIdAndUnidad(UUID tenantId, InventoryUnit unidad);

    Page<Inventory> findAllByTenantIdAndUnidad(UUID tenantId, InventoryUnit unidad, Pageable pageable);

    @Query("SELECT i FROM Inventory i WHERE i.tenant.id = :tenantId AND i.isActive = true AND i.quantity <= i.minimumStock")
    List<Inventory> findLowStockByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT i FROM Inventory i WHERE i.tenant.id = :tenantId AND i.isActive = true AND i.quantity <= i.minimumStock")
    Page<Inventory> findLowStockByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndIsActiveTrue(UUID tenantId);

    @Query("SELECT COUNT(i) FROM Inventory i WHERE i.tenant.id = :tenantId AND i.isActive = true AND i.quantity <= i.minimumStock")
    long countLowStockByTenantId(@Param("tenantId") UUID tenantId);
}
