package lat.nexofood.api.modules.catalog.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.catalog.application.service.ProductService;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductAvailabilityRequest;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductRequest;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductStockRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/products")
@RequiredArgsConstructor
@Tag(name = "Catalog - Products", description = "Gestión de productos del catálogo del restaurante")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Listar productos del restaurante (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getProducts(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Boolean availableOnly,
            @RequestParam(required = false) Boolean inStockOnly,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<ProductResponse> page = productService.findAllByTenant(
                tenantId,
                categoryId,
                availableOnly,
                inStockOnly,
                pageable
        );
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<ProductResponse>>builder()
                        .success(true)
                        .message("Productos obtenidos exitosamente")
                        .data(page)
                        .build()
        );
    }

    @GetMapping("/{productId}")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Obtener producto por ID (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @PathVariable UUID tenantId,
            @PathVariable UUID productId
    ) {
        ProductResponse product = productService.findByIdAndTenant(tenantId, productId);
        return ResponseEntity.ok(
                ApiResponse.<ProductResponse>builder()
                        .success(true)
                        .message("Producto obtenido exitosamente")
                        .data(product)
                        .build()
        );
    }

    @PostMapping
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Crear un nuevo producto en el catálogo (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @PathVariable UUID tenantId,
            @Valid @RequestBody ProductRequest request
    ) {
        ProductResponse created = productService.create(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<ProductResponse>builder()
                        .success(true)
                        .message("Producto creado exitosamente")
                        .data(created)
                        .build()
        );
    }

    @PutMapping("/{productId}")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Actualizar un producto existente (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable UUID tenantId,
            @PathVariable UUID productId,
            @Valid @RequestBody ProductRequest request
    ) {
        ProductResponse updated = productService.update(tenantId, productId, request);
        return ResponseEntity.ok(
                ApiResponse.<ProductResponse>builder()
                        .success(true)
                        .message("Producto actualizado exitosamente")
                        .data(updated)
                        .build()
        );
    }

    @PatchMapping("/{productId}/availability")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Activar o desactivar la disponibilidad de un producto (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<ProductResponse>> toggleAvailability(
            @PathVariable UUID tenantId,
            @PathVariable UUID productId,
            @Valid @RequestBody ProductAvailabilityRequest request
    ) {
        ProductResponse updated = productService.toggleAvailability(tenantId, productId, request.isAvailable());
        return ResponseEntity.ok(
                ApiResponse.<ProductResponse>builder()
                        .success(true)
                        .message(Boolean.TRUE.equals(request.isAvailable())
                                ? "Producto marcado como disponible"
                                : "Producto marcado como no disponible")
                        .data(updated)
                        .build()
        );
    }

    @PatchMapping("/{productId}/stock")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Actualizar el stock disponible de un producto (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<ProductResponse>> updateStock(
            @PathVariable UUID tenantId,
            @PathVariable UUID productId,
            @Valid @RequestBody ProductStockRequest request
    ) {
        ProductResponse updated = productService.updateStock(tenantId, productId, request.stock());
        return ResponseEntity.ok(
                ApiResponse.<ProductResponse>builder()
                        .success(true)
                        .message("Stock del producto actualizado exitosamente")
                        .data(updated)
                        .build()
        );
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Deshabilitar producto (soft delete: isAvailable = false) (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable UUID tenantId,
            @PathVariable UUID productId
    ) {
        productService.delete(tenantId, productId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Producto deshabilitado exitosamente")
                        .build()
        );
    }
}
