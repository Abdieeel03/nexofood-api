package lat.nexofood.api.modules.identity.application.usecase.address;

import lat.nexofood.api.modules.identity.web.dto.response.CustomerAddressResponse;

import java.util.List;

public interface GetMyAddressesUseCase {
    List<CustomerAddressResponse> execute();
}
