package lat.nexofood.api.modules.catalog.repository;

import lat.nexofood.api.modules.catalog.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findAllByTenantId(UUID tenantId);

    List<Product> findAllByTenantIdAndIsAvailableTrue(UUID tenantId);

    List<Product> findAllByTenantIdAndCategoryId(UUID tenantId, UUID categoryId);

    Optional<Product> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);
}
