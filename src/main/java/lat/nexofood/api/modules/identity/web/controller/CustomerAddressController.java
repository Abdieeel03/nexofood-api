package lat.nexofood.api.modules.identity.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.identity.application.service.address.CustomerAddressService;
import lat.nexofood.api.modules.identity.web.dto.request.CustomerAddressRequest;
import lat.nexofood.api.modules.identity.web.dto.response.CustomerAddressResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/addresses")
@RequiredArgsConstructor
@Tag(name = "Direcciones del Cliente", description = "Gestión de direcciones de entrega del usuario autenticado")
public class CustomerAddressController {

    private final CustomerAddressService customerAddressService;

    @GetMapping
    @Operation(summary = "Listar mis direcciones guardadas")
    public ResponseEntity<ApiResponse<List<CustomerAddressResponse>>> getMyAddresses() {
        List<CustomerAddressResponse> addresses = customerAddressService.getMyAddresses();
        return ResponseEntity.ok(
                ApiResponse.<List<CustomerAddressResponse>>builder()
                        .success(true)
                        .message("Direcciones obtenidas exitosamente")
                        .data(addresses)
                        .build()
        );
    }

    @PostMapping
    @Operation(summary = "Registrar nueva dirección de entrega")
    public ResponseEntity<ApiResponse<CustomerAddressResponse>> createAddress(
            @Valid @RequestBody CustomerAddressRequest request) {
        CustomerAddressResponse created = customerAddressService.createAddress(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<CustomerAddressResponse>builder()
                        .success(true)
                        .message("Dirección creada exitosamente")
                        .data(created)
                        .build()
        );
    }

    @PutMapping("/{addressId}")
    @Operation(summary = "Actualizar una dirección guardada")
    public ResponseEntity<ApiResponse<CustomerAddressResponse>> updateAddress(
            @PathVariable UUID addressId,
            @Valid @RequestBody CustomerAddressRequest request) {
        CustomerAddressResponse updated = customerAddressService.updateAddress(addressId, request);
        return ResponseEntity.ok(
                ApiResponse.<CustomerAddressResponse>builder()
                        .success(true)
                        .message("Dirección actualizada exitosamente")
                        .data(updated)
                        .build()
        );
    }

    @DeleteMapping("/{addressId}")
    @Operation(summary = "Eliminar una dirección guardada")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(@PathVariable UUID addressId) {
        customerAddressService.deleteAddress(addressId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Dirección eliminada exitosamente")
                        .build()
        );
    }
}
