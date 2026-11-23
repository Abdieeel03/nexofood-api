package lat.nexofood.api.modules.order.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.order.application.service.DriverOrderService;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.web.dto.request.DriverOrderStatusRequest;
import lat.nexofood.api.modules.order.web.dto.response.DriverOrderResponse;
import lat.nexofood.api.modules.tracking.application.service.DeliveryTrackingService;
import lat.nexofood.api.modules.tracking.web.dto.request.DriverLocationUpdateRequest;
import lat.nexofood.api.modules.tracking.web.dto.response.DeliveryTrackingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/driver")
@RequiredArgsConstructor
@PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).REPARTIDOR)")
@Tag(name = "Driver - Delivery", description = "Endpoints dedicados para repartidores bajo mínimo privilegio")
public class DriverController {

    private final DriverOrderService driverOrderService;
    private final DeliveryTrackingService deliveryTrackingService;

    @GetMapping("/orders")
    @Operation(summary = "Listar órdenes asignadas al repartidor autenticado (excluye PENDIENTE y CANCELADO)")
    public ResponseEntity<ApiResponse<List<DriverOrderResponse>>> getDriverOrders(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) OrderStatus status
    ) {
        List<DriverOrderResponse> response = driverOrderService.getOrders(tenantId, status);
        return ResponseEntity.ok(
                ApiResponse.<List<DriverOrderResponse>>builder()
                        .success(true)
                        .message("Órdenes de reparto obtenidas exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Obtener detalle logístico de una orden asignada al repartidor")
    public ResponseEntity<ApiResponse<DriverOrderResponse>> getDriverOrderById(
            @PathVariable UUID tenantId,
            @PathVariable UUID orderId
    ) {
        DriverOrderResponse response = driverOrderService.getOrderById(tenantId, orderId);
        return ResponseEntity.ok(
                ApiResponse.<DriverOrderResponse>builder()
                        .success(true)
                        .message("Orden de reparto obtenida exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PatchMapping("/orders/{orderId}/status")
    @Operation(summary = "Actualizar estado de una orden asignada al repartidor (EN_CAMINO o ENTREGADO)")
    public ResponseEntity<ApiResponse<DriverOrderResponse>> updateDriverOrderStatus(
            @PathVariable UUID tenantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody DriverOrderStatusRequest request
    ) {
        DriverOrderResponse response = driverOrderService.updateStatus(tenantId, orderId, request.status());
        return ResponseEntity.ok(
                ApiResponse.<DriverOrderResponse>builder()
                        .success(true)
                        .message("Estado de la orden de reparto actualizado exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PutMapping("/orders/{orderId}/location")
    @Operation(summary = "Enviar ping GPS del repartidor asignado para recalcular ETA")
    public ResponseEntity<ApiResponse<DeliveryTrackingResponse>> updateDriverLocation(
            @PathVariable UUID tenantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody DriverLocationUpdateRequest request
    ) {
        DeliveryTrackingResponse response = deliveryTrackingService.updateDriverLocation(tenantId, orderId, request);
        return ResponseEntity.ok(
                ApiResponse.<DeliveryTrackingResponse>builder()
                        .success(true)
                        .message("Ubicación del repartidor actualizada exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/orders/{orderId}/tracking")
    @Operation(summary = "Consultar rastreo GPS de una orden asignada al repartidor")
    public ResponseEntity<ApiResponse<DeliveryTrackingResponse>> getDriverOrderTracking(
            @PathVariable UUID tenantId,
            @PathVariable UUID orderId,
            @RequestParam(defaultValue = "false") boolean includeHistory
    ) {
        DeliveryTrackingResponse response = deliveryTrackingService.getOrderTrackingForDriver(
                tenantId,
                orderId,
                includeHistory
        );
        return ResponseEntity.ok(
                ApiResponse.<DeliveryTrackingResponse>builder()
                        .success(true)
                        .message("Seguimiento de la orden obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }
}
