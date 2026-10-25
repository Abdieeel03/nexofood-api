package lat.nexofood.api.modules.identity.application.service.address;

import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.common.util.GeoUtils;
import lat.nexofood.api.modules.identity.application.usecase.address.CreateAddressUseCase;
import lat.nexofood.api.modules.identity.domain.CustomerAddress;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.CustomerAddressRepository;
import lat.nexofood.api.modules.identity.web.dto.request.CustomerAddressRequest;
import lat.nexofood.api.modules.identity.web.dto.response.CustomerAddressResponse;
import lat.nexofood.api.modules.identity.web.mapper.CustomerAddressMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateAddressService implements CreateAddressUseCase {

    private final CustomerAddressRepository customerAddressRepository;
    private final CurrentUserProvider currentUserProvider;
    private final CustomerAddressMapper customerAddressMapper;

    @Override
    @Transactional
    public CustomerAddressResponse execute(CustomerAddressRequest request) {
        GeoUtils.validateCoordinates(request.latitude(), request.longitude());
        User user = currentUserProvider.getCurrentUser();

        CustomerAddress address = customerAddressMapper.toEntity(request, user);
        CustomerAddress saved = customerAddressRepository.save(address);

        log.info("Dirección creada con ID {} para el usuario {}", saved.getId(), user.getId());
        return customerAddressMapper.toResponse(saved);
    }
}
