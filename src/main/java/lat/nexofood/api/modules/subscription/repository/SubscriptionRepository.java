package lat.nexofood.api.modules.subscription.repository;

import lat.nexofood.api.modules.subscription.domain.Subscription;
import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findAllByUserId(UUID userId);

    Optional<Subscription> findByIdAndUserId(UUID id, UUID userId);

    List<Subscription> findAllByStatus(SubscriptionStatus status);

    Optional<Subscription> findByMpPreapprovalId(String mpPreapprovalId);
}
