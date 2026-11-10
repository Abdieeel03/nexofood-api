package lat.nexofood.api.modules.store.application.usecase.member;

import java.util.UUID;

public interface RemoveTenantMemberUseCase {
    void execute(UUID tenantId, UUID memberId);
}
