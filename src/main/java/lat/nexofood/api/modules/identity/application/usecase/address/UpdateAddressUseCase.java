package lat.nexofood.api.modules.identity.application.usecase.address;

import lat.nexofood.api.modules.identity.web.dto.request.CustomerAddressRequest;
import lat.nexofood.api.modules.identity.web.dto.response.CustomerAddressResponse;

import java.util.UUID;

public interface UpdateAddressUseCase {
    CustomerAddressResponse execute(UUID addressId, CustomerAddressRequest request);
}
