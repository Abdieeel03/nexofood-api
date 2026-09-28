package lat.nexofood.api.modules.store.infrastructure.repository;

import lat.nexofood.api.modules.store.domain.TenantCustomer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantCustomerRepository extends JpaRepository<TenantCustomer, UUID> {

    Optional<TenantCustomer> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserId(UUID tenantId, UUID userId);

    Page<TenantCustomer> findByTenantId(UUID tenantId, Pageable pageable);

    @Query("SELECT tc FROM TenantCustomer tc JOIN tc.tenant t JOIN tc.user u " +
           "WHERE t.slug = :slug AND u.email = :email")
    Optional<TenantCustomer> findByTenantSlugAndUserEmail(
            @Param("slug") String slug,
            @Param("email") String email);
}
