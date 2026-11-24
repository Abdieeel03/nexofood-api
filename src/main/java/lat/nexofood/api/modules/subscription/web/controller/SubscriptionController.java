package lat.nexofood.api.modules.subscription.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.subscription.application.service.subscription.SubscriptionBillingService;
import lat.nexofood.api.modules.subscription.application.service.subscription.SubscriptionService;
import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import lat.nexofood.api.modules.subscription.web.dto.request.SubscriptionCheckoutRequest;
import lat.nexofood.api.modules.subscription.web.dto.request.SubscriptionCreateRequest;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionCheckoutResponse;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionPaymentConfigResponse;
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

    @GetMapping("/config")
    @Operation(summary = "Obtener configuración pública de Mercado Pago para pagos de suscripción con tarjeta")
    public ResponseEntity<ApiResponse<SubscriptionPaymentConfigResponse>> getPaymentConfig() {
        SubscriptionPaymentConfigResponse response = subscriptionBillingService.getPaymentConfig();
        return ResponseEntity.ok(
                ApiResponse.<SubscriptionPaymentConfigResponse>builder()
                        .success(true)
                        .message("Configuración de pagos de suscripción obtenida exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PostMapping("/checkout")
    @Operation(summary = "Iniciar checkout de suscripción en Mercado Pago (pago directo con tarjeta tokenizada o enlace initPoint)")
    public ResponseEntity<ApiResponse<SubscriptionCheckoutResponse>> checkoutSubscription(
            @Valid @RequestBody SubscriptionCheckoutRequest request) {
        SubscriptionCheckoutResponse response = subscriptionBillingService.startCheckout(request);
        String message = response.status() == SubscriptionStatus.ACTIVE
                ? "Suscripción activada exitosamente"
                : "Enlace de suscripción preaprobada generado exitosamente";
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<SubscriptionCheckoutResponse>builder()
                        .success(true)
                        .message(message)
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

    @PostMapping("/me/sync")
    @Operation(summary = "Sincronizar el estado de mi suscripción directamente con Mercado Pago (útil tras retornar del checkout)")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> syncMySubscription() {
        SubscriptionResponse response = subscriptionBillingService.syncMySubscription();
        return ResponseEntity.ok(
                ApiResponse.<SubscriptionResponse>builder()
                        .success(true)
                        .message("Suscripción sincronizada con Mercado Pago exitosamente")
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
