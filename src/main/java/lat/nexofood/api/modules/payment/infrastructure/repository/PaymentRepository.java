package lat.nexofood.api.modules.payment.infrastructure.repository;

import jakarta.persistence.LockModeType;
import lat.nexofood.api.modules.payment.domain.Payment;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    Optional<Payment> findByMpOrderId(String mpOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.mpOrderId = :mpOrderId")
    Optional<Payment> findByMpOrderIdForUpdate(@Param("mpOrderId") String mpOrderId);

    Optional<Payment> findByMpPaymentId(String mpPaymentId);

    Optional<Payment> findByIdAndTenantIdAndCustomerId(UUID id, UUID tenantId, UUID customerId);

    List<Payment> findAllByTenantIdAndCustomerIdAndStatusIn(
            UUID tenantId,
            UUID customerId,
            Collection<PaymentStatus> statuses
    );

    List<Payment> findAllByTenantId(UUID tenantId);

    Page<Payment> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<Payment> findAllByTenantIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId,
            PaymentStatus status,
            Pageable pageable
    );

    List<Payment> findAllByTenantIdAndStatus(UUID tenantId, PaymentStatus status);

    Optional<Payment> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Payment> findTop100ByStatusInAndUpdatedAtBeforeAndCreatedAtAfter(
            Collection<PaymentStatus> statuses,
            OffsetDateTime updatedBefore,
            OffsetDateTime createdAfter
    );

    List<Payment> findTop100ByStatusAndExpiresAtBefore(
            PaymentStatus status,
            OffsetDateTime expiresBefore
    );
}
