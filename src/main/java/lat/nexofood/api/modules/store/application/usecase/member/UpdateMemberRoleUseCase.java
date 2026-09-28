package lat.nexofood.api.modules.store.application.usecase.member;

import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.web.dto.response.TenantMemberResponse;

import java.util.UUID;

public interface UpdateMemberRoleUseCase {
    TenantMemberResponse execute(UUID tenantId, UUID memberId, TenantStaffRole newRole);
}
