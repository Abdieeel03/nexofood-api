package lat.nexofood.api.modules.catalog.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.catalog.application.service.ProductPriceService;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductPriceRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.ProductPriceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/products/{productId}/prices")
@RequiredArgsConstructor
@Tag(name = "Catalog - Product Prices", description = "Gestión de reglas de precios dinámicos y promociones por producto")
public class ProductPriceController {

    private final ProductPriceService productPriceService;

    @GetMapping
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Listar todas las reglas de precio de un producto (acceso para todo el staff)")
    public ResponseEntity<ApiResponse<List<ProductPriceResponse>>> getPrices(
            @PathVariable UUID tenantId,
            @PathVariable UUID productId
    ) {
        List<ProductPriceResponse> prices = productPriceService.findAllByProduct(tenantId, productId);
        return ResponseEntity.ok(
                ApiResponse.<List<ProductPriceResponse>>builder()
                        .success(true)
                        .message("Reglas de precio obtenidas exitosamente")
                        .data(prices)
                        .build()
        );
    }

    @GetMapping("/{priceId}")
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Obtener una regla de precio por ID (acceso para todo el staff)")
    public ResponseEntity<ApiResponse<ProductPriceResponse>> getPriceById(
            @PathVariable UUID tenantId,
            @PathVariable UUID productId,
            @PathVariable UUID priceId
    ) {
        ProductPriceResponse price = productPriceService.findById(tenantId, productId, priceId);
        return ResponseEntity.ok(
                ApiResponse.<ProductPriceResponse>builder()
                        .success(true)
                        .message("Regla de precio obtenida exitosamente")
                        .data(price)
                        .build()
        );
    }

    @PostMapping
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Agregar una nueva regla de precio o promoción al producto (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<ProductPriceResponse>> createPrice(
            @PathVariable UUID tenantId,
            @PathVariable UUID productId,
            @Valid @RequestBody ProductPriceRequest request
    ) {
        ProductPriceResponse created = productPriceService.create(tenantId, productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<ProductPriceResponse>builder()
                        .success(true)
                        .message("Regla de precio creada exitosamente")
                        .data(created)
                        .build()
        );
    }

    @PutMapping("/{priceId}")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Actualizar una regla de precio de un producto (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<ProductPriceResponse>> updatePrice(
            @PathVariable UUID tenantId,
            @PathVariable UUID productId,
            @PathVariable UUID priceId,
            @Valid @RequestBody ProductPriceRequest request
    ) {
        ProductPriceResponse updated = productPriceService.update(tenantId, productId, priceId, request);
        return ResponseEntity.ok(
                ApiResponse.<ProductPriceResponse>builder()
                        .success(true)
                        .message("Regla de precio actualizada exitosamente")
                        .data(updated)
                        .build()
        );
    }

    @DeleteMapping("/{priceId}")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Eliminar una regla de precio (no permite eliminar el precio base activo) (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<Void>> deletePrice(
            @PathVariable UUID tenantId,
            @PathVariable UUID productId,
            @PathVariable UUID priceId
    ) {
        productPriceService.delete(tenantId, productId, priceId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Regla de precio eliminada exitosamente")
                        .build()
        );
    }
}
