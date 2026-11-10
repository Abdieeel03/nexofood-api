package lat.nexofood.api.modules.payment.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.payment.application.service.OrderPaymentService;
import lat.nexofood.api.modules.payment.web.dto.response.PaymentStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/store/{slug}/payments")
@RequiredArgsConstructor
@Tag(name = "Storefront - Payments", description = "Consulta del estado del pago y orden generada tras Checkout Pro")
public class StorefrontPaymentController {

    private final OrderPaymentService orderPaymentService;

    @GetMapping("/{paymentId}")
    @Operation(summary = "Consultar el estado de mi pago en la tienda y el ID de la orden generada")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getMyPaymentStatus(
            @PathVariable String slug,
            @PathVariable UUID paymentId
    ) {
        PaymentStatusResponse response = orderPaymentService.getCustomerPaymentStatus(slug, paymentId);
        return ResponseEntity.ok(
                ApiResponse.<PaymentStatusResponse>builder()
                        .success(true)
                        .message("Estado del pago obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }
}
