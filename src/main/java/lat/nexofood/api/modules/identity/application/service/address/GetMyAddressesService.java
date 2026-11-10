package lat.nexofood.api.modules.identity.application.service.address;

import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.application.usecase.address.GetMyAddressesUseCase;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.CustomerAddressRepository;
import lat.nexofood.api.modules.identity.web.dto.response.CustomerAddressResponse;
import lat.nexofood.api.modules.identity.web.mapper.CustomerAddressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetMyAddressesService implements GetMyAddressesUseCase {

    private final CustomerAddressRepository customerAddressRepository;
    private final CurrentUserProvider currentUserProvider;
    private final CustomerAddressMapper customerAddressMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerAddressResponse> execute() {
        User user = currentUserProvider.getCurrentUser();
        return customerAddressRepository.findAllByUserId(user.getId())
                .stream()
                .map(customerAddressMapper::toResponse)
                .toList();
    }
}
