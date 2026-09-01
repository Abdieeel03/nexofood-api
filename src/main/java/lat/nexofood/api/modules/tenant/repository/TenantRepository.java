package lat.nexofood.api.modules.tenant.repository;

import lat.nexofood.api.modules.tenant.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Tenant> findAllByOwnerId(UUID ownerId);

    Optional<Tenant> findBySubscriptionId(UUID subscriptionId);
}
