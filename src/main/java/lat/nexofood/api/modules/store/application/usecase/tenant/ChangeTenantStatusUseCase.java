package lat.nexofood.api.modules.store.application.usecase.tenant;

import java.util.UUID;

public interface ChangeTenantStatusUseCase {
    void execute(UUID id, boolean isActive);
}
