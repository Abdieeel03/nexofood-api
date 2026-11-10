package lat.nexofood.api.modules.tracking.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.tracking.application.service.DeliveryTrackingService;
import lat.nexofood.api.modules.tracking.web.dto.response.DeliveryEstimateResponse;
import lat.nexofood.api.modules.tracking.web.dto.response.DeliveryTrackingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/store/{slug}")
@RequiredArgsConstructor
@Tag(name = "Storefront - Tracking & Delivery Estimate", description = "Seguimiento en vivo del repartidor para el cliente y estimación previa de cobertura y tiempo de entrega")
public class StorefrontTrackingController {

    private final DeliveryTrackingService trackingService;

    @GetMapping("/orders/{orderId}/tracking")
    @Operation(summary = "Cliente: consultar posición actual del repartidor y tiempo estimado de llegada (polling cada ~15 s)")
    public ResponseEntity<ApiResponse<DeliveryTrackingResponse>> getMyOrderTracking(
            @PathVariable String slug,
            @PathVariable UUID orderId
    ) {
        DeliveryTrackingResponse response = trackingService.getOrderTrackingForCustomer(slug, orderId);
        return ResponseEntity.ok(
                ApiResponse.<DeliveryTrackingResponse>builder()
                        .success(true)
                        .message("Seguimiento del pedido obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/delivery-estimate")
    @Operation(summary = "Público: verificar cobertura de entrega, tarifa y tiempo estimado (ETA) hacia unas coordenadas")
    public ResponseEntity<ApiResponse<DeliveryEstimateResponse>> estimateDelivery(
            @PathVariable String slug,
            @RequestParam("lat") Double latitude,
            @RequestParam("lng") Double longitude
    ) {
        DeliveryEstimateResponse response = trackingService.estimateDeliveryBySlug(slug, latitude, longitude);
        return ResponseEntity.ok(
                ApiResponse.<DeliveryEstimateResponse>builder()
                        .success(true)
                        .message("Estimación de entrega calculada exitosamente")
                        .data(response)
                        .build()
        );
    }
}

