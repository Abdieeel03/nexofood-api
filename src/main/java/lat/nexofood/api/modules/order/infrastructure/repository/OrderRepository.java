package lat.nexofood.api.modules.order.infrastructure.repository;

import jakarta.persistence.LockModeType;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    List<Order> findAllByTenantId(UUID tenantId);

    Page<Order> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    List<Order> findAllByTenantIdAndStatus(UUID tenantId, OrderStatus status);

    List<Order> findAllByTenantIdAndStatusOrderByCreatedAtAsc(UUID tenantId, OrderStatus status);

    Optional<Order> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.tenant.id = :tenantId")
    Optional<Order> findByIdAndTenantIdForUpdate(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId
    );

    Optional<Order> findByIdAndTenantIdAndCustomerUserId(UUID id, UUID tenantId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.tenant.id = :tenantId AND o.customerId = :userId")
    Optional<Order> findByIdAndTenantIdAndCustomerUserIdForUpdate(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId
    );

    List<Order> findAllByCustomerUserId(UUID userId);

    List<Order> findAllByTenantIdAndCustomerUserId(UUID tenantId, UUID userId);

    Page<Order> findAllByTenantIdAndCustomerUserIdOrderByCreatedAtDesc(
            UUID tenantId,
            UUID userId,
            Pageable pageable
    );

    List<Order> findAllByDeliveryStaffId(UUID deliveryStaffId);

    List<Order> findAllByTenantIdAndDeliveryStaffIdOrderByCreatedAtAsc(UUID tenantId, UUID deliveryStaffId);

    List<Order> findAllByTenantIdAndDeliveryStaffIdAndStatusOrderByCreatedAtAsc(
            UUID tenantId,
            UUID deliveryStaffId,
            OrderStatus status
    );

    @Query("""
            SELECT o FROM Order o
            WHERE o.tenant.id = :tenantId
              AND (:status IS NULL OR o.status = :status)
              AND (:deliveryType IS NULL OR o.deliveryType = :deliveryType)
              AND (:fromDate IS NULL OR o.createdAt >= :fromDate)
              AND (:toDate IS NULL OR o.createdAt <= :toDate)
            ORDER BY o.createdAt DESC
            """)
    Page<Order> findByTenantWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("status") OrderStatus status,
            @Param("deliveryType") DeliveryType deliveryType,
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate,
            Pageable pageable
    );

    long countByTenantId(UUID tenantId);

    boolean existsByOrderNumberAndTenantId(String orderNumber, UUID tenantId);
}
