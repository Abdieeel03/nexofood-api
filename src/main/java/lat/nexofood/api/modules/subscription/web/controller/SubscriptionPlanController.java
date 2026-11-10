package lat.nexofood.api.modules.subscription.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.subscription.application.service.plan.SubscriptionPlanService;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionPlanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
@Tag(name = "Subscription Plans", description = "Consulta pública de planes SaaS disponibles")
public class SubscriptionPlanController {

    private final SubscriptionPlanService subscriptionPlanService;

    @GetMapping
    @Operation(summary = "Listar planes activos de suscripción (público)")
    public ResponseEntity<ApiResponse<List<SubscriptionPlanResponse>>> getActivePlans() {
        List<SubscriptionPlanResponse> plans = subscriptionPlanService.getActivePlans();
        return ResponseEntity.ok(
                ApiResponse.<List<SubscriptionPlanResponse>>builder()
                        .success(true)
                        .message("Planes de suscripción obtenidos exitosamente")
                        .data(plans)
                        .build()
        );
    }
}
