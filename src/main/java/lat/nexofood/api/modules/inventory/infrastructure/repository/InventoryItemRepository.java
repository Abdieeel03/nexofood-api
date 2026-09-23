package lat.nexofood.api.modules.inventory.infrastructure.repository;

import lat.nexofood.api.modules.inventory.domain.InventoryItem;
import lat.nexofood.api.modules.inventory.domain.InventoryUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    Optional<InventoryItem> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<InventoryItem> findByTenantIdAndSku(UUID tenantId, String sku);

    boolean existsByTenantIdAndSku(UUID tenantId, String sku);

    List<InventoryItem> findAllByTenantId(UUID tenantId);

    Page<InventoryItem> findAllByTenantId(UUID tenantId, Pageable pageable);

    List<InventoryItem> findAllByTenantIdAndIsActiveTrue(UUID tenantId);

    Page<InventoryItem> findAllByTenantIdAndIsActiveTrue(UUID tenantId, Pageable pageable);

    List<InventoryItem> findAllByTenantIdAndCategory(UUID tenantId, String category);

    Page<InventoryItem> findAllByTenantIdAndCategory(UUID tenantId, String category, Pageable pageable);

    List<InventoryItem> findAllByTenantIdAndUnidad(UUID tenantId, InventoryUnit unidad);

    Page<InventoryItem> findAllByTenantIdAndUnidad(UUID tenantId, InventoryUnit unidad, Pageable pageable);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndIsActiveTrue(UUID tenantId);
}
