package lat.nexofood.api.modules.identity.application.service.address;

import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.application.usecase.address.DeleteAddressUseCase;
import lat.nexofood.api.modules.identity.domain.CustomerAddress;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.CustomerAddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteAddressService implements DeleteAddressUseCase {

    private final CustomerAddressRepository customerAddressRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public void execute(UUID addressId) {
        User user = currentUserProvider.getCurrentUser();

        CustomerAddress address = customerAddressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Dirección no encontrada"));

        customerAddressRepository.delete(address);
        log.info("Dirección {} eliminada para el usuario {}", addressId, user.getId());
    }
}
