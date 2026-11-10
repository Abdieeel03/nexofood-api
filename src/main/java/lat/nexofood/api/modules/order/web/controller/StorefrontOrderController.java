package lat.nexofood.api.modules.order.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.order.application.service.CheckoutService;
import lat.nexofood.api.modules.order.application.service.OrderService;
import lat.nexofood.api.modules.order.web.dto.request.OrderCancelRequest;
import lat.nexofood.api.modules.order.web.dto.request.OrderCheckoutRequest;
import lat.nexofood.api.modules.order.web.dto.response.OrderResponse;
import lat.nexofood.api.modules.payment.web.dto.response.CheckoutPaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/store/{slug}/orders")
@RequiredArgsConstructor
@Tag(name = "Storefront - Orders", description = "Checkout con Mercado Pago (Orders API), cancelación con reembolso e historial de pedidos del cliente")
public class StorefrontOrderController {

    private final CheckoutService checkoutService;
    private final OrderService orderService;

    @PostMapping("/checkout")
    @Operation(summary = "Iniciar checkout desde el carrito activo en la tienda (congela snapshot y genera orden de pago Checkout Pro en Mercado Pago)")
    public ResponseEntity<ApiResponse<CheckoutPaymentResponse>> checkout(
            @PathVariable String slug,
            @Valid @RequestBody OrderCheckoutRequest request
    ) {
        CheckoutPaymentResponse response = checkoutService.createFromCart(slug, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<CheckoutPaymentResponse>builder()
                        .success(true)
                        .message("Orden de pago generada exitosamente en Mercado Pago")
                        .data(response)
                        .build()
        );
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancelar mi pedido y recibir reembolso automático (solo permitido mientras el estado sea PENDIENTE)")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelMyOrder(
            @PathVariable String slug,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) OrderCancelRequest request
    ) {
        String reason = request != null ? request.reason() : null;
        OrderResponse response = orderService.cancelByCustomer(slug, id, reason);
        return ResponseEntity.ok(
                ApiResponse.<OrderResponse>builder()
                        .success(true)
                        .message("Pedido cancelado y reembolsado exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping
    @Operation(summary = "Listar historial paginado de mis pedidos en la tienda")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getMyOrders(
            @PathVariable String slug,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<OrderResponse> response = orderService.getMyOrdersBySlug(slug, pageable);
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<OrderResponse>>builder()
                        .success(true)
                        .message("Historial de pedidos obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle de uno de mis pedidos en la tienda")
    public ResponseEntity<ApiResponse<OrderResponse>> getMyOrderDetail(
            @PathVariable String slug,
            @PathVariable UUID id
    ) {
        OrderResponse response = orderService.getMyOrderDetailBySlug(slug, id);
        return ResponseEntity.ok(
                ApiResponse.<OrderResponse>builder()
                        .success(true)
                        .message("Detalle del pedido obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }
}
