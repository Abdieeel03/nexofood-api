package lat.nexofood.api.modules.payment.repository;

import lat.nexofood.api.modules.payment.domain.Payment;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByMpPaymentId(String mpPaymentId);

    Optional<Payment> findByMpPreferenceId(String mpPreferenceId);

    List<Payment> findAllByTenantId(UUID tenantId);

    List<Payment> findAllByTenantIdAndStatus(UUID tenantId, PaymentStatus status);

    Optional<Payment> findByIdAndTenantId(UUID id, UUID tenantId);
}
