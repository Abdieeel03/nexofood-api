package lat.nexofood.api.modules.store.infrastructure.repository;

import lat.nexofood.api.modules.store.domain.TenantInvitation;
import lat.nexofood.api.modules.store.domain.TenantInvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantInvitationRepository extends JpaRepository<TenantInvitation, UUID> {

    boolean existsByTenantIdAndInvitedUserIdAndStatus(UUID tenantId, UUID invitedUserId, TenantInvitationStatus status);

    List<TenantInvitation> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<TenantInvitation> findAllByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, TenantInvitationStatus status);

    List<TenantInvitation> findAllByInvitedUserIdAndStatusOrderByCreatedAtDesc(UUID invitedUserId, TenantInvitationStatus status);

    Optional<TenantInvitation> findByIdAndInvitedUserId(UUID id, UUID invitedUserId);
}
