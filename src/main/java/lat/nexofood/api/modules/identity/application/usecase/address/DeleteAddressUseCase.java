package lat.nexofood.api.modules.identity.application.usecase.address;

import java.util.UUID;

public interface DeleteAddressUseCase {
    void execute(UUID addressId);
}
