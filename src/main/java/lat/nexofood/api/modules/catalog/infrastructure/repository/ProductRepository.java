package lat.nexofood.api.modules.catalog.infrastructure.repository;

import jakarta.persistence.LockModeType;
import lat.nexofood.api.modules.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    List<Product> findAllByTenantId(UUID tenantId);

    Page<Product> findAllByTenantId(UUID tenantId, Pageable pageable);

    List<Product> findAllByTenantIdAndIsAvailableTrue(UUID tenantId);

    Page<Product> findAllByTenantIdAndIsAvailableTrue(UUID tenantId, Pageable pageable);

    List<Product> findAllByTenantIdAndCategoryId(UUID tenantId, UUID categoryId);

    Page<Product> findAllByTenantIdAndCategoryId(UUID tenantId, UUID categoryId, Pageable pageable);

    List<Product> findAllByTenantIdAndCategoryIdAndIsAvailableTrue(UUID tenantId, UUID categoryId);

    Page<Product> findAllByTenantIdAndCategoryIdAndIsAvailableTrue(UUID tenantId, UUID categoryId, Pageable pageable);

    Page<Product> findAllByTenantIdAndCategoryIdAndIsAvailableTrueAndNameContainingIgnoreCase(
            UUID tenantId,
            UUID categoryId,
            String name,
            Pageable pageable
    );

    @Query("""
            SELECT p FROM Product p
            LEFT JOIN p.category c
            WHERE p.tenant.id = :tenantId
              AND p.isAvailable = true
              AND (c IS NULL OR c.isActive = true)
            """)
    Page<Product> findStorefrontProductsByTenant(
            @Param("tenantId") UUID tenantId,
            Pageable pageable
    );

    @Query("""
            SELECT p FROM Product p
            LEFT JOIN p.category c
            WHERE p.tenant.id = :tenantId
              AND p.isAvailable = true
              AND (c IS NULL OR c.isActive = true)
              AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<Product> findStorefrontProductsByTenantAndSearch(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search,
            Pageable pageable
    );

    Optional<Product> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.tenant.id = :tenantId")
    Optional<Product> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    long countByTenantId(UUID tenantId);

    boolean existsByTaxId(UUID taxId);

    boolean existsByCategoryId(UUID categoryId);
}
