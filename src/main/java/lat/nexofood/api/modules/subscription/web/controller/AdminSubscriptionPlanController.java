package lat.nexofood.api.modules.subscription.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.subscription.application.service.plan.SubscriptionPlanService;
import lat.nexofood.api.modules.subscription.web.dto.request.SubscriptionPlanRequest;
import lat.nexofood.api.modules.subscription.web.dto.request.SubscriptionPlanStatusRequest;
import lat.nexofood.api.modules.subscription.web.dto.response.SubscriptionPlanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/plans")
@RequiredArgsConstructor
@PreAuthorize("@tenantAccess.isSuperAdmin()")
@Tag(name = "Administración de Planes (SUPERADMIN)", description = "Gestión de planes de suscripción SaaS")
public class AdminSubscriptionPlanController {

    private final SubscriptionPlanService subscriptionPlanService;

    @GetMapping
    @Operation(summary = "Listar todos los planes (incluyendo inactivos)")
    public ResponseEntity<ApiResponse<List<SubscriptionPlanResponse>>> getAllPlans() {
        List<SubscriptionPlanResponse> plans = subscriptionPlanService.getAllPlans();
        return ResponseEntity.ok(
                ApiResponse.<List<SubscriptionPlanResponse>>builder()
                        .success(true)
                        .message("Todos los planes obtenidos exitosamente")
                        .data(plans)
                        .build()
        );
    }

    @GetMapping("/{planId}")
    @Operation(summary = "Obtener detalle de un plan por ID")
    public ResponseEntity<ApiResponse<SubscriptionPlanResponse>> getPlanById(@PathVariable UUID planId) {
        SubscriptionPlanResponse plan = subscriptionPlanService.getPlanById(planId);
        return ResponseEntity.ok(
                ApiResponse.<SubscriptionPlanResponse>builder()
                        .success(true)
                        .message("Plan obtenido exitosamente")
                        .data(plan)
                        .build()
        );
    }

    @PostMapping
    @Operation(summary = "Crear un nuevo plan de suscripción")
    public ResponseEntity<ApiResponse<SubscriptionPlanResponse>> createPlan(
            @Valid @RequestBody SubscriptionPlanRequest request) {
        SubscriptionPlanResponse created = subscriptionPlanService.createPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<SubscriptionPlanResponse>builder()
                        .success(true)
                        .message("Plan de suscripción creado exitosamente")
                        .data(created)
                        .build()
        );
    }

    @PutMapping("/{planId}")
    @Operation(summary = "Actualizar un plan de suscripción")
    public ResponseEntity<ApiResponse<SubscriptionPlanResponse>> updatePlan(
            @PathVariable UUID planId,
            @Valid @RequestBody SubscriptionPlanRequest request) {
        SubscriptionPlanResponse updated = subscriptionPlanService.updatePlan(planId, request);
        return ResponseEntity.ok(
                ApiResponse.<SubscriptionPlanResponse>builder()
                        .success(true)
                        .message("Plan de suscripción actualizado exitosamente")
                        .data(updated)
                        .build()
        );
    }

    @PatchMapping("/{planId}/status")
    @Operation(summary = "Activar o desactivar un plan de suscripción")
    public ResponseEntity<ApiResponse<SubscriptionPlanResponse>> changeStatus(
            @PathVariable UUID planId,
            @Valid @RequestBody SubscriptionPlanStatusRequest request) {
        SubscriptionPlanResponse updated = subscriptionPlanService.changeStatus(planId, request.isActive());
        return ResponseEntity.ok(
                ApiResponse.<SubscriptionPlanResponse>builder()
                        .success(true)
                        .message(Boolean.TRUE.equals(request.isActive()) ? "Plan activado exitosamente" : "Plan desactivado exitosamente")
                        .data(updated)
                        .build()
        );
    }
}
