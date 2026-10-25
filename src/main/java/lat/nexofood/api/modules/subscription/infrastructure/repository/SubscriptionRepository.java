package lat.nexofood.api.modules.subscription.infrastructure.repository;

import lat.nexofood.api.modules.subscription.domain.Subscription;
import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findAllByUserId(UUID userId);

    Optional<Subscription> findByIdAndUserId(UUID id, UUID userId);

    Optional<Subscription> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
            UUID userId,
            Collection<SubscriptionStatus> statuses
    );

    Optional<Subscription> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndStatusIn(UUID userId, Collection<SubscriptionStatus> statuses);

    List<Subscription> findAllByStatus(SubscriptionStatus status);

    List<Subscription> findAllByStatusInAndEndDateBefore(
            Collection<SubscriptionStatus> statuses,
            OffsetDateTime threshold
    );

    Optional<Subscription> findByMpPreapprovalId(String mpPreapprovalId);
}
