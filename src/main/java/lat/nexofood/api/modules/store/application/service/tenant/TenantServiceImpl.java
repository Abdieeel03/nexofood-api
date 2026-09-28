package lat.nexofood.api.modules.store.application.service.tenant;

import lat.nexofood.api.modules.store.application.usecase.tenant.*;
import lat.nexofood.api.modules.store.application.usecase.member.*;
import lat.nexofood.api.modules.store.application.usecase.customer.*;
import lat.nexofood.api.modules.store.application.usecase.auth.*;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.web.dto.request.TenantCreateRequest;
import lat.nexofood.api.modules.store.web.dto.request.TenantMemberRequest;
import lat.nexofood.api.modules.store.web.dto.request.TenantUpdateRequest;
import lat.nexofood.api.modules.store.web.dto.response.TenantMemberResponse;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final CreateTenantUseCase createTenantUseCase;
    private final GetTenantByIdUseCase getTenantByIdUseCase;
    private final GetTenantBySlugUseCase getTenantBySlugUseCase;
    private final UpdateTenantUseCase updateTenantUseCase;
    private final ChangeTenantStatusUseCase changeTenantStatusUseCase;
    private final GetMyTenantUseCase getMyTenantUseCase;
    private final GetTenantMembersUseCase getTenantMembersUseCase;
    private final AddTenantMemberUseCase addTenantMemberUseCase;
    private final UpdateMemberRoleUseCase updateMemberRoleUseCase;
    private final RemoveTenantMemberUseCase removeTenantMemberUseCase;

    @Override
    public TenantResponse createTenant(TenantCreateRequest request) {
        return createTenantUseCase.execute(request);
    }

    @Override
    public TenantResponse getTenantById(UUID id) {
        return getTenantByIdUseCase.execute(id);
    }

    @Override
    public TenantResponse getTenantBySlug(String slug) {
        return getTenantBySlugUseCase.execute(slug);
    }

    @Override
    public TenantResponse updateTenant(UUID id, TenantUpdateRequest request) {
        return updateTenantUseCase.execute(id, request);
    }

    @Override
    public void changeStatus(UUID id, boolean isActive) {
        changeTenantStatusUseCase.execute(id, isActive);
    }

    @Override
    public TenantResponse getMyTenant(String email) {
        return getMyTenantUseCase.execute(email);
    }

    @Override
    public List<TenantMemberResponse> getMembers(UUID tenantId) {
        return getTenantMembersUseCase.execute(tenantId);
    }

    @Override
    public TenantMemberResponse addMember(UUID tenantId, TenantMemberRequest request) {
        return addTenantMemberUseCase.execute(tenantId, request);
    }

    @Override
    public TenantMemberResponse updateMemberRole(UUID tenantId, UUID memberId, TenantStaffRole newRole) {
        return updateMemberRoleUseCase.execute(tenantId, memberId, newRole);
    }

    @Override
    public void removeMember(UUID tenantId, UUID memberId) {
        removeTenantMemberUseCase.execute(tenantId, memberId);
    }
}
