package lat.nexofood.api.modules.store.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.catalog.web.dto.response.CategoryResponse;
import lat.nexofood.api.modules.store.application.service.storefront.StorefrontMenuService;
import lat.nexofood.api.modules.store.web.dto.response.StorefrontMenuResponse;
import lat.nexofood.api.modules.store.web.dto.response.StorefrontProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/store/{slug}")
@RequiredArgsConstructor
@Tag(name = "Storefront - Catalog & Menu", description = "Menú público del restaurante para clientes (categorías activas y productos disponibles con precio vigente e impuestos)")
public class StorefrontCatalogController {

    private final StorefrontMenuService storefrontMenuService;

    @GetMapping("/menu")
    @Operation(summary = "Obtener menú público completo del restaurante por slug (categorías activas y productos disponibles)")
    public ResponseEntity<ApiResponse<StorefrontMenuResponse>> getMenu(@PathVariable String slug) {
        StorefrontMenuResponse menu = storefrontMenuService.getMenuBySlug(slug);
        return ResponseEntity.ok(
                ApiResponse.<StorefrontMenuResponse>builder()
                        .success(true)
                        .message("Menú obtenido exitosamente")
                        .data(menu)
                        .build()
        );
    }

    @GetMapping("/categories")
    @Operation(summary = "Listar categorías activas de la tienda ordenadas por sortOrder (público)")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories(@PathVariable String slug) {
        List<CategoryResponse> categories = storefrontMenuService.getActiveCategoriesBySlug(slug);
        return ResponseEntity.ok(
                ApiResponse.<List<CategoryResponse>>builder()
                        .success(true)
                        .message("Categorías de la tienda obtenidas exitosamente")
                        .data(categories)
                        .build()
        );
    }

    @GetMapping("/products")
    @Operation(summary = "Listar productos disponibles de la tienda con paginación y filtro por categoría o búsqueda (público)")
    public ResponseEntity<ApiResponse<PageResponse<StorefrontProductResponse>>> getProducts(
            @PathVariable String slug,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<StorefrontProductResponse> page = storefrontMenuService.getProductsBySlug(
                slug,
                categoryId,
                search,
                pageable
        );
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<StorefrontProductResponse>>builder()
                        .success(true)
                        .message("Productos de la tienda obtenidos exitosamente")
                        .data(page)
                        .build()
        );
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Obtener detalle público de un producto con su precio vigente e impuesto")
    public ResponseEntity<ApiResponse<StorefrontProductResponse>> getProductDetail(
            @PathVariable String slug,
            @PathVariable UUID id
    ) {
        StorefrontProductResponse product = storefrontMenuService.getProductDetailBySlug(slug, id);
        return ResponseEntity.ok(
                ApiResponse.<StorefrontProductResponse>builder()
                        .success(true)
                        .message("Producto obtenido exitosamente")
                        .data(product)
                        .build()
        );
    }
}
