package lat.nexofood.api.modules.subscription.infrastructure.repository;

import lat.nexofood.api.modules.subscription.domain.SubscriptionPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, UUID> {

    Optional<SubscriptionPayment> findByMpAuthorizedPaymentId(String mpAuthorizedPaymentId);

    List<SubscriptionPayment> findAllBySubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId);
}
