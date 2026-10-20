package lat.nexofood.api.modules.store.infrastructure.repository;

import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.domain.TenantCustomerId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantCustomerRepository extends JpaRepository<TenantCustomer, TenantCustomerId> {

    Page<TenantCustomer> findByIdTenantId(UUID tenantId, Pageable pageable);

    @Query("SELECT tc FROM TenantCustomer tc JOIN tc.tenant t JOIN tc.user u " +
            "WHERE t.slug = :slug AND u.email = :email")
    Optional<TenantCustomer> findByTenantSlugAndUserEmail(
            @Param("slug") String slug,
            @Param("email") String email);
}