package lat.nexofood.api.modules.order.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.order.application.service.KitchenOrderService;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.web.dto.request.KitchenOrderStatusRequest;
import lat.nexofood.api.modules.order.web.dto.response.KitchenOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/kitchen")
@RequiredArgsConstructor
@PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).COCINERO)")
@Tag(name = "Kitchen - KDS", description = "Endpoints dedicados para cocina (KDS) bajo mínimo privilegio")
public class KitchenController {

    private final KitchenOrderService kitchenOrderService;

    @GetMapping("/orders")
    @Operation(summary = "Listar cola de cocina (PENDIENTE pagados y EN_PREPARACION en orden FIFO)")
    public ResponseEntity<ApiResponse<List<KitchenOrderResponse>>> getKitchenOrders(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) OrderStatus status
    ) {
        List<KitchenOrderResponse> response = kitchenOrderService.getOrders(tenantId, status);
        return ResponseEntity.ok(
                ApiResponse.<List<KitchenOrderResponse>>builder()
                        .success(true)
                        .message("Cola de cocina obtenida exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Obtener detalle operativo de una orden visible en cocina")
    public ResponseEntity<ApiResponse<KitchenOrderResponse>> getKitchenOrderById(
            @PathVariable UUID tenantId,
            @PathVariable UUID orderId
    ) {
        KitchenOrderResponse response = kitchenOrderService.getOrderById(tenantId, orderId);
        return ResponseEntity.ok(
                ApiResponse.<KitchenOrderResponse>builder()
                        .success(true)
                        .message("Orden de cocina obtenida exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PatchMapping("/orders/{orderId}/status")
    @Operation(summary = "Actualizar estado de una orden en cocina (EN_PREPARACION o LISTO_PARA_ENTREGA)")
    public ResponseEntity<ApiResponse<KitchenOrderResponse>> updateKitchenOrderStatus(
            @PathVariable UUID tenantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody KitchenOrderStatusRequest request
    ) {
        KitchenOrderResponse response = kitchenOrderService.updateStatus(tenantId, orderId, request.status());
        return ResponseEntity.ok(
                ApiResponse.<KitchenOrderResponse>builder()
                        .success(true)
                        .message("Estado de la orden en cocina actualizado exitosamente")
                        .data(response)
                        .build()
        );
    }
}
