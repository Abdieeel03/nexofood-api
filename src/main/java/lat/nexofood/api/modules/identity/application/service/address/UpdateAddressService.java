package lat.nexofood.api.modules.identity.application.service.address;

import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.common.util.GeoUtils;
import lat.nexofood.api.modules.identity.application.usecase.address.UpdateAddressUseCase;
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

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateAddressService implements UpdateAddressUseCase {

    private final CustomerAddressRepository customerAddressRepository;
    private final CurrentUserProvider currentUserProvider;
    private final CustomerAddressMapper customerAddressMapper;

    @Override
    @Transactional
    public CustomerAddressResponse execute(UUID addressId, CustomerAddressRequest request) {
        GeoUtils.validateCoordinates(request.latitude(), request.longitude());
        User user = currentUserProvider.getCurrentUser();

        CustomerAddress address = customerAddressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Dirección no encontrada"));

        if (request.title() != null && !request.title().isBlank()) {
            address.setTitle(request.title());
        }
        address.setAddressLine(request.addressLine());
        address.setReference(request.reference());
        address.setLocation(GeoUtils.createPoint(request.latitude(), request.longitude()));

        CustomerAddress updated = customerAddressRepository.save(address);
        log.info("Dirección {} actualizada para el usuario {}", addressId, user.getId());
        return customerAddressMapper.toResponse(updated);
    }
}
