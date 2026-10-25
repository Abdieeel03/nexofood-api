package lat.nexofood.api.modules.identity.application.service.address;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.domain.CustomerAddress;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.identity.infrastructure.repository.CustomerAddressRepository;
import lat.nexofood.api.modules.identity.web.dto.request.CustomerAddressRequest;
import lat.nexofood.api.modules.identity.web.dto.response.CustomerAddressResponse;
import lat.nexofood.api.modules.identity.web.mapper.CustomerAddressMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerAddressServiceTest {

    @Mock
    private CustomerAddressRepository customerAddressRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Spy
    private CustomerAddressMapper customerAddressMapper = new CustomerAddressMapper();

    private CreateAddressService createAddressService;
    private GetMyAddressesService getMyAddressesService;
    private UpdateAddressService updateAddressService;
    private DeleteAddressService deleteAddressService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        createAddressService = new CreateAddressService(customerAddressRepository, currentUserProvider, customerAddressMapper);
        getMyAddressesService = new GetMyAddressesService(customerAddressRepository, currentUserProvider, customerAddressMapper);
        updateAddressService = new UpdateAddressService(customerAddressRepository, currentUserProvider, customerAddressMapper);
        deleteAddressService = new DeleteAddressService(customerAddressRepository, currentUserProvider);

        currentUser = User.builder()
                .id(UUID.randomUUID())
                .email("cliente@nexofood.lat")
                .fullName("Carlos Cliente")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Crea dirección asociada al usuario autenticado y valida coordenadas")
    void createsAddressSuccessfully() {
        CustomerAddressRequest request = CustomerAddressRequest.builder()
                .title("Oficina")
                .addressLine("Av. Javier Prado 123")
                .reference("Frente al banco")
                .latitude(-12.0901)
                .longitude(-77.0221)
                .build();

        when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(customerAddressRepository.save(any(CustomerAddress.class))).thenAnswer(inv -> {
            CustomerAddress saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        CustomerAddressResponse response = createAddressService.execute(request);

        assertNotNull(response);
        assertEquals(currentUser.getId(), response.userId());
        assertEquals("Oficina", response.title());
        assertEquals(-12.0901, response.latitude());
        assertEquals(-77.0221, response.longitude());
    }

    @Test
    @DisplayName("Lanza BadRequestException cuando las coordenadas están fuera de rango")
    void throwsBadRequestWhenInvalidCoordinates() {
        CustomerAddressRequest invalidRequest = CustomerAddressRequest.builder()
                .title("Casa")
                .addressLine("Calle 1")
                .latitude(-99.5) // fuera de [-90, 90]
                .longitude(-77.0)
                .build();

        assertThrows(BadRequestException.class, () -> createAddressService.execute(invalidRequest));
        verify(customerAddressRepository, never()).save(any());
    }

    @Test
    @DisplayName("Impide actualizar o eliminar una dirección que pertenece a otro usuario")
    void preventsModifyingOtherUserAddress() {
        UUID addressId = UUID.randomUUID();
        CustomerAddressRequest request = CustomerAddressRequest.builder()
                .title("Casa")
                .addressLine("Calle Nueva 456")
                .latitude(-12.05)
                .longitude(-77.04)
                .build();

        when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(customerAddressRepository.findByIdAndUserId(addressId, currentUser.getId())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> updateAddressService.execute(addressId, request));
        assertThrows(ResourceNotFoundException.class, () -> deleteAddressService.execute(addressId));
        verify(customerAddressRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Lista únicamente las direcciones del usuario autenticado")
    void listsOnlyCurrentUserAddresses() {
        when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
        CustomerAddress addr = customerAddressMapper.toEntity(
                CustomerAddressRequest.builder()
                        .title("Casa")
                        .addressLine("Av. Larco 456")
                        .latitude(-12.12)
                        .longitude(-77.03)
                        .build(),
                currentUser
        );
        addr.setId(UUID.randomUUID());
        when(customerAddressRepository.findAllByUserId(currentUser.getId())).thenReturn(List.of(addr));

        List<CustomerAddressResponse> result = getMyAddressesService.execute();

        assertEquals(1, result.size());
        assertEquals("Av. Larco 456", result.getFirst().addressLine());
    }
}
