package lat.nexofood.api.modules.store.application.service.tenant;

import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.web.dto.request.TenantCreateRequest;
import lat.nexofood.api.modules.store.web.dto.request.TenantMemberRequest;
import lat.nexofood.api.modules.store.web.dto.request.TenantUpdateRequest;
import lat.nexofood.api.modules.store.web.dto.response.TenantMemberResponse;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;

import java.util.List;
import java.util.UUID;

public interface TenantService {

    TenantResponse createTenant(TenantCreateRequest request);

    TenantResponse getTenantById(UUID id);

    TenantResponse getTenantBySlug(String slug);

    TenantResponse updateTenant(UUID id, TenantUpdateRequest request);

    void changeStatus(UUID id, boolean isActive);

    TenantResponse getMyTenant(String email);

    List<TenantMemberResponse> getMembers(UUID tenantId);

    TenantMemberResponse addMember(UUID tenantId, TenantMemberRequest request);

    TenantMemberResponse updateMemberRole(UUID tenantId, UUID memberId, TenantStaffRole newRole);

    void removeMember(UUID tenantId, UUID memberId);
}
