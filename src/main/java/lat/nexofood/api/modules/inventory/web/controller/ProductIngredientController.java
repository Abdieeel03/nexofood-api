package lat.nexofood.api.modules.inventory.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.inventory.application.service.ProductIngredientService;
import lat.nexofood.api.modules.inventory.web.dto.request.ProductIngredientRequest;
import lat.nexofood.api.modules.inventory.web.dto.response.ProductIngredientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/products/{productId}/ingredients")
@RequiredArgsConstructor
@Tag(name = "Inventory - Product Recipes", description = "Gestión de recetas de productos (insumos requeridos por unidad vendida)")
public class ProductIngredientController {

    private final ProductIngredientService productIngredientService;

    @GetMapping
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Listar los ingredientes (receta) de un producto del restaurante")
    public ResponseEntity<ApiResponse<List<ProductIngredientResponse>>> getProductRecipe(
            @PathVariable UUID tenantId,
            @PathVariable UUID productId
    ) {
        List<ProductIngredientResponse> recipe = productIngredientService.getRecipeByProduct(tenantId, productId);
        return ResponseEntity.ok(
                ApiResponse.<List<ProductIngredientResponse>>builder()
                        .success(true)
                        .message("Receta del producto obtenida exitosamente")
                        .data(recipe)
                        .build()
        );
    }

    @PostMapping
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Agregar o actualizar un ingrediente en la receta de un producto (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<ProductIngredientResponse>> addOrUpdateIngredient(
            @PathVariable UUID tenantId,
            @PathVariable UUID productId,
            @Valid @RequestBody ProductIngredientRequest request
    ) {
        ProductIngredientResponse saved = productIngredientService.addOrUpdateIngredient(tenantId, productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<ProductIngredientResponse>builder()
                        .success(true)
                        .message("Ingrediente asignado a la receta exitosamente")
                        .data(saved)
                        .build()
        );
    }

    @DeleteMapping("/{ingredientId}")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Eliminar un ingrediente de la receta de un producto (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<Void>> removeIngredient(
            @PathVariable UUID tenantId,
            @PathVariable UUID productId,
            @PathVariable UUID ingredientId
    ) {
        productIngredientService.removeIngredient(tenantId, productId, ingredientId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Ingrediente eliminado de la receta exitosamente")
                        .build()
        );
    }
}
