package lat.nexofood.api.modules.subscription.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.subscription.application.service.subscription.SubscriptionService;
import lat.nexofood.api.modules.subscription.web.dto.request.SubscriptionCreateRequest;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "Gestión de la suscripción SaaS del propietario del restaurante")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

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

    @PatchMapping("/me/cancel")
    @Operation(summary = "Cancelar la suscripción vigente del usuario autenticado")
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
