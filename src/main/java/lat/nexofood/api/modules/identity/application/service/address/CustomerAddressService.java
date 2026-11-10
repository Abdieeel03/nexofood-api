package lat.nexofood.api.modules.identity.application.service.address;

import lat.nexofood.api.modules.identity.web.dto.request.CustomerAddressRequest;
import lat.nexofood.api.modules.identity.web.dto.response.CustomerAddressResponse;

import java.util.List;
import java.util.UUID;

public interface CustomerAddressService {

    CustomerAddressResponse createAddress(CustomerAddressRequest request);

    List<CustomerAddressResponse> getMyAddresses();

    CustomerAddressResponse updateAddress(UUID addressId, CustomerAddressRequest request);

    void deleteAddress(UUID addressId);
}
