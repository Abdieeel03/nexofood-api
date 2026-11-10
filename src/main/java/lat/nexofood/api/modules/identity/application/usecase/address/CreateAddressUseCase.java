package lat.nexofood.api.modules.identity.application.usecase.address;

import lat.nexofood.api.modules.identity.web.dto.request.CustomerAddressRequest;
import lat.nexofood.api.modules.identity.web.dto.response.CustomerAddressResponse;

public interface CreateAddressUseCase {
    CustomerAddressResponse execute(CustomerAddressRequest request);
}
