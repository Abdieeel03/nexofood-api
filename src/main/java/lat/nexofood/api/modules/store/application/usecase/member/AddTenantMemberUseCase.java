package lat.nexofood.api.modules.store.application.usecase.member;

import lat.nexofood.api.modules.store.web.dto.request.TenantMemberRequest;
import lat.nexofood.api.modules.store.web.dto.response.TenantMemberResponse;

import java.util.UUID;

public interface AddTenantMemberUseCase {
    TenantMemberResponse execute(UUID tenantId, TenantMemberRequest request);
}
