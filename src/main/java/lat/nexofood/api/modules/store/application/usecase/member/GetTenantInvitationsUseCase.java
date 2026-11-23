package lat.nexofood.api.modules.store.application.usecase.member;

import lat.nexofood.api.modules.store.domain.TenantInvitationStatus;
import lat.nexofood.api.modules.store.web.dto.response.TenantMemberResponse;

import java.util.List;
import java.util.UUID;

public interface GetTenantInvitationsUseCase {

    List<TenantMemberResponse> execute(UUID tenantId, TenantInvitationStatus status);
}
