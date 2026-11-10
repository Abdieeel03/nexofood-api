package lat.nexofood.api.modules.tracking.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.tracking.application.service.DeliveryTrackingService;
import lat.nexofood.api.modules.tracking.web.dto.request.DriverLocationUpdateRequest;
import lat.nexofood.api.modules.tracking.web.dto.response.DeliveryTrackingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
@RequiredArgsConstructor
@Tag(name = "Tracking - Driver & Backoffice", description = "Rastreo GPS en tiempo real del repartidor, cálculo dinámico de ETA y mapa activo del restaurante")
public class DeliveryTrackingController {

    private final DeliveryTrackingService trackingService;

    @PutMapping("/orders/{orderId}/tracking/location")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).REPARTIDOR)")
    @Operation(summary = "Repartidor: enviar ping periódico de coordenadas GPS (cada 10-15 s) y recalcular ETA restante")
    public ResponseEntity<ApiResponse<DeliveryTrackingResponse>> updateDriverLocation(
            @PathVariable UUID tenantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody DriverLocationUpdateRequest request
    ) {
        DeliveryTrackingResponse response = trackingService.updateDriverLocation(tenantId, orderId, request);
        return ResponseEntity.ok(
                ApiResponse.<DeliveryTrackingResponse>builder()
                        .success(true)
                        .message("Ubicación del repartidor actualizada exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/orders/{orderId}/tracking")
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Backoffice / Repartidor: obtener posición actual del repartidor, ETA y recorrido opcional de la orden")
    public ResponseEntity<ApiResponse<DeliveryTrackingResponse>> getOrderTracking(
            @PathVariable UUID tenantId,
            @PathVariable UUID orderId,
            @RequestParam(defaultValue = "false") boolean includeHistory
    ) {
        DeliveryTrackingResponse response = trackingService.getOrderTrackingForStaff(tenantId, orderId, includeHistory);
        return ResponseEntity.ok(
                ApiResponse.<DeliveryTrackingResponse>builder()
                        .success(true)
                        .message("Estado de seguimiento de la orden obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/tracking/active")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Backoffice: listar todos los repartidores en ruta (EN_CAMINO) con su posición actual y ETA")
    public ResponseEntity<ApiResponse<List<DeliveryTrackingResponse>>> getActiveDeliveries(
            @PathVariable UUID tenantId
    ) {
        List<DeliveryTrackingResponse> response = trackingService.getActiveTrackingsByTenant(tenantId);
        return ResponseEntity.ok(
                ApiResponse.<List<DeliveryTrackingResponse>>builder()
                        .success(true)
                        .message("Entregas activas en ruta obtenidas exitosamente")
                        .data(response)
                        .build()
        );
    }
}

