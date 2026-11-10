package lat.nexofood.api.modules.identity.application.service.address;

import lat.nexofood.api.modules.identity.application.usecase.address.CreateAddressUseCase;
import lat.nexofood.api.modules.identity.application.usecase.address.DeleteAddressUseCase;
import lat.nexofood.api.modules.identity.application.usecase.address.GetMyAddressesUseCase;
import lat.nexofood.api.modules.identity.application.usecase.address.UpdateAddressUseCase;
import lat.nexofood.api.modules.identity.web.dto.request.CustomerAddressRequest;
import lat.nexofood.api.modules.identity.web.dto.response.CustomerAddressResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerAddressServiceImpl implements CustomerAddressService {

    private final CreateAddressUseCase createAddressUseCase;
    private final GetMyAddressesUseCase getMyAddressesUseCase;
    private final UpdateAddressUseCase updateAddressUseCase;
    private final DeleteAddressUseCase deleteAddressUseCase;

    @Override
    public CustomerAddressResponse createAddress(CustomerAddressRequest request) {
        return createAddressUseCase.execute(request);
    }

    @Override
    public List<CustomerAddressResponse> getMyAddresses() {
        return getMyAddressesUseCase.execute();
    }

    @Override
    public CustomerAddressResponse updateAddress(UUID addressId, CustomerAddressRequest request) {
        return updateAddressUseCase.execute(addressId, request);
    }

    @Override
    public void deleteAddress(UUID addressId) {
        deleteAddressUseCase.execute(addressId);
    }
}
