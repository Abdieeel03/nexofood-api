package lat.nexofood.api.modules.notification.infrastructure.repository;

import lat.nexofood.api.modules.notification.domain.Notification;
import lat.nexofood.api.modules.notification.domain.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findAllByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    Page<Notification> findAllByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    Page<Notification> findAllByRecipientIdAndTypeOrderByCreatedAtDesc(
            UUID recipientId,
            NotificationType type,
            Pageable pageable
    );

    Page<Notification> findAllByRecipientIdAndIsReadFalseAndTypeOrderByCreatedAtDesc(
            UUID recipientId,
            NotificationType type,
            Pageable pageable
    );

    List<Notification> findAllByRecipientIdAndIsReadFalse(UUID recipientId);

    long countByRecipientIdAndIsReadFalse(UUID recipientId);

    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    Optional<Notification> findByReferenceIdAndType(UUID referenceId, NotificationType type);

    boolean existsByRecipientIdAndDedupKey(UUID recipientId, String dedupKey);
}
