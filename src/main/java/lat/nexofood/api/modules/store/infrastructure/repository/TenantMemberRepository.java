package lat.nexofood.api.modules.store.infrastructure.repository;

import lat.nexofood.api.modules.store.domain.TenantMember;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantMemberRepository extends JpaRepository<TenantMember, UUID> {

    List<TenantMember> findAllByTenantId(UUID tenantId);

    List<TenantMember> findAllByUserId(UUID userId);

    Optional<TenantMember> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    List<TenantMember> findAllByTenantIdAndRole(UUID tenantId, TenantStaffRole role);

    boolean existsByTenantIdAndUserId(UUID tenantId, UUID userId);
}
