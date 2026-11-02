package lat.nexofood.api.modules.order.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.order.application.service.OrderService;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.web.dto.request.OrderAssignStaffRequest;
import lat.nexofood.api.modules.order.web.dto.request.OrderStatusUpdateRequest;
import lat.nexofood.api.modules.order.web.dto.response.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/orders")
@RequiredArgsConstructor
@Tag(name = "Order - Backoffice, KDS & Delivery", description = "Gestión administrativa, cocina (KDS) y reparto de órdenes del restaurante")
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Listar órdenes paginadas del restaurante con filtros opcionales (estado, modalidad, fechas)")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getOrders(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) DeliveryType deliveryType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toDate,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<OrderResponse> response = orderService.findByTenantPaginated(
                tenantId,
                status,
                deliveryType,
                fromDate,
                toDate,
                pageable
        );
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<OrderResponse>>builder()
                        .success(true)
                        .message("Órdenes del restaurante obtenidas exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/kds")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).COCINERO)")
    @Operation(summary = "Cola KDS (Cocina): obtener órdenes EN_PREPARACION ordenadas por antigüedad (FIFO)")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getKitchenOrders(@PathVariable UUID tenantId) {
        List<OrderResponse> response = orderService.getKitchenOrders(tenantId);
        return ResponseEntity.ok(
                ApiResponse.<List<OrderResponse>>builder()
                        .success(true)
                        .message("Cola de cocina (KDS) obtenida exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/my-deliveries")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).REPARTIDOR)")
    @Operation(summary = "Repartidor: obtener órdenes asignadas al repartidor autenticado")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyAssignedDeliveries(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) OrderStatus status
    ) {
        List<OrderResponse> response = orderService.getMyAssignedDeliveries(tenantId, status);
        return ResponseEntity.ok(
                ApiResponse.<List<OrderResponse>>builder()
                        .success(true)
                        .message("Órdenes asignadas de reparto obtenidas exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Obtener detalle de una orden del restaurante por ID")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @PathVariable UUID tenantId,
            @PathVariable UUID id
    ) {
        OrderResponse response = orderService.findByIdAndTenant(id, tenantId);
        return ResponseEntity.ok(
                ApiResponse.<OrderResponse>builder()
                        .success(true)
                        .message("Orden obtenida exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Cambiar estado de la orden (validado por máquina de estados y rol del miembro)")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody OrderStatusUpdateRequest request
    ) {
        OrderResponse response = orderService.updateStatus(tenantId, id, request.status());
        return ResponseEntity.ok(
                ApiResponse.<OrderResponse>builder()
                        .success(true)
                        .message("Estado de la orden actualizado exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Asignar un repartidor activo del restaurante a una orden DELIVERY")
    public ResponseEntity<ApiResponse<OrderResponse>> assignDeliveryStaff(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody OrderAssignStaffRequest request
    ) {
        OrderResponse response = orderService.assignDeliveryStaff(tenantId, id, request.deliveryStaffId());
        return ResponseEntity.ok(
                ApiResponse.<OrderResponse>builder()
                        .success(true)
                        .message("Repartidor asignado exitosamente a la orden")
                        .data(response)
                        .build()
        );
    }
}
