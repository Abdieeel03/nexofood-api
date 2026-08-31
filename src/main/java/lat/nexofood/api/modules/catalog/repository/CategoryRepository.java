package lat.nexofood.api.modules.catalog.repository;

import lat.nexofood.api.modules.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByTenantId(UUID tenantId);

    List<Category> findAllByTenantIdAndIsActiveTrueOrderBySortOrderAsc(UUID tenantId);

    Optional<Category> findByIdAndTenantId(UUID id, UUID tenantId);
}
