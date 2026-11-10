package lat.nexofood.api.modules.catalog.infrastructure.repository;

import lat.nexofood.api.modules.catalog.domain.Tax;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxRepository extends JpaRepository<Tax, UUID> {

    List<Tax> findAllByTenantId(UUID tenantId);

    List<Tax> findAllByTenantIdAndIsActiveTrue(UUID tenantId);

    Optional<Tax> findByIdAndTenantId(UUID id, UUID tenantId);
}
