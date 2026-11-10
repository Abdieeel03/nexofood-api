package lat.nexofood.api.modules.inventory.infrastructure.repository;

import lat.nexofood.api.modules.inventory.domain.ProductIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductIngredientRepository extends JpaRepository<ProductIngredient, UUID> {

    List<ProductIngredient> findAllByTenantIdAndProductId(UUID tenantId, UUID productId);

    Optional<ProductIngredient> findByIdAndTenantIdAndProductId(UUID id, UUID tenantId, UUID productId);

    Optional<ProductIngredient> findByTenantIdAndProductIdAndInventoryItemId(
            UUID tenantId,
            UUID productId,
            UUID inventoryItemId
    );

    boolean existsByTenantIdAndProductIdAndInventoryItemId(
            UUID tenantId,
            UUID productId,
            UUID inventoryItemId
    );

    boolean existsByTenantIdAndInventoryItemId(UUID tenantId, UUID inventoryItemId);
}
