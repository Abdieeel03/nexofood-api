package lat.nexofood.api.modules.subscription.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.subscription.application.service.subscription.SubscriptionBillingService;
import lat.nexofood.api.modules.subscription.application.service.subscription.SubscriptionService;
import lat.nexofood.api.modules.subscription.web.dto.request.SubscriptionCreateRequest;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionCheckoutResponse;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionPaymentResponse;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "Gestión de la suscripción SaaS y cobro recurrente con pagos preaprobados en Mercado Pago")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionBillingService subscriptionBillingService;

    @PostMapping
    @Operation(summary = "Iniciar suscripción en período de prueba (TRIAL)")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> startTrial(
            @Valid @RequestBody SubscriptionCreateRequest request) {
        SubscriptionResponse response = subscriptionService.startTrial(request.planId());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<SubscriptionResponse>builder()
                        .success(true)
                        .message("Suscripción iniciada exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PostMapping("/checkout")
    @Operation(summary = "Generar suscripción preaprobada en Mercado Pago (devuelve initPoint para autorizar pagos recurrentes)")
    public ResponseEntity<ApiResponse<SubscriptionCheckoutResponse>> checkoutSubscription(
            @Valid @RequestBody SubscriptionCreateRequest request) {
        SubscriptionCheckoutResponse response = subscriptionBillingService.startCheckout(request.planId());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<SubscriptionCheckoutResponse>builder()
                        .success(true)
                        .message("Enlace de suscripción preaprobada generado exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/me")
    @Operation(summary = "Obtener la suscripción del usuario autenticado")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getMySubscription() {
        SubscriptionResponse response = subscriptionService.getMySubscription();
        return ResponseEntity.ok(
                ApiResponse.<SubscriptionResponse>builder()
                        .success(true)
                        .message("Suscripción obtenida exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/me/payments")
    @Operation(summary = "Listar historial de cobros recurrentes de mi suscripción")
    public ResponseEntity<ApiResponse<List<SubscriptionPaymentResponse>>> getMySubscriptionPayments() {
        List<SubscriptionPaymentResponse> response = subscriptionBillingService.getMyPayments();
        return ResponseEntity.ok(
                ApiResponse.<List<SubscriptionPaymentResponse>>builder()
                        .success(true)
                        .message("Historial de cobros de suscripción obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PatchMapping("/me/cancel")
    @Operation(summary = "Cancelar la suscripción vigente (en suscripciones pagadas mantiene el acceso abierto hasta el fin del período)")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> cancelMySubscription() {
        SubscriptionResponse response = subscriptionService.cancel();
        return ResponseEntity.ok(
                ApiResponse.<SubscriptionResponse>builder()
                        .success(true)
                        .message("Suscripción cancelada exitosamente")
                        .data(response)
                        .build()
        );
    }
}
