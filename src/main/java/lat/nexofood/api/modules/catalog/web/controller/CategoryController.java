package lat.nexofood.api.modules.catalog.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.catalog.application.service.CategoryService;
import lat.nexofood.api.modules.catalog.web.dto.request.CategoryRequest;
import lat.nexofood.api.modules.catalog.web.dto.request.CategorySortOrderRequest;
import lat.nexofood.api.modules.catalog.web.dto.request.CategoryStatusRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.CategoryResponse;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/categories")
@RequiredArgsConstructor
@Tag(name = "Catalog - Categories", description = "Gestión de categorías del catálogo del restaurante")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Listar categorías del restaurante ordenadas por sortOrder (acceso para todo el staff)")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
        List<CategoryResponse> categories = activeOnly
                ? categoryService.findActiveByTenant(tenantId)
                : categoryService.findAllByTenant(tenantId);

        return ResponseEntity.ok(
                ApiResponse.<List<CategoryResponse>>builder()
                        .success(true)
                        .message("Categorías obtenidas exitosamente")
                        .data(categories)
                        .build()
        );
    }

    @GetMapping("/{categoryId}")
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Obtener categoría por ID (acceso para todo el staff)")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(
            @PathVariable UUID tenantId,
            @PathVariable UUID categoryId
    ) {
        CategoryResponse category = categoryService.findByIdAndTenant(tenantId, categoryId);
        return ResponseEntity.ok(
                ApiResponse.<CategoryResponse>builder()
                        .success(true)
                        .message("Categoría obtenida exitosamente")
                        .data(category)
                        .build()
        );
    }

    @PostMapping
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Crear una nueva categoría (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CategoryRequest request
    ) {
        CategoryResponse created = categoryService.create(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<CategoryResponse>builder()
                        .success(true)
                        .message("Categoría creada exitosamente")
                        .data(created)
                        .build()
        );
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Actualizar una categoría (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable UUID tenantId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody CategoryRequest request
    ) {
        CategoryResponse updated = categoryService.update(tenantId, categoryId, request);
        return ResponseEntity.ok(
                ApiResponse.<CategoryResponse>builder()
                        .success(true)
                        .message("Categoría actualizada exitosamente")
                        .data(updated)
                        .build()
        );
    }

    @PatchMapping("/{categoryId}/status")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Activar o desactivar una categoría (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<CategoryResponse>> toggleCategoryStatus(
            @PathVariable UUID tenantId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody CategoryStatusRequest request
    ) {
        CategoryResponse updated = categoryService.toggleStatus(tenantId, categoryId, request.isActive());
        return ResponseEntity.ok(
                ApiResponse.<CategoryResponse>builder()
                        .success(true)
                        .message(Boolean.TRUE.equals(request.isActive())
                                ? "Categoría activada exitosamente"
                                : "Categoría desactivada exitosamente")
                        .data(updated)
                        .build()
        );
    }

    @PatchMapping("/{categoryId}/sort-order")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Reordenar la posición (sortOrder) de una categoría (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<CategoryResponse>> reorderCategory(
            @PathVariable UUID tenantId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody CategorySortOrderRequest request
    ) {
        CategoryResponse updated = categoryService.reorder(tenantId, categoryId, request.sortOrder());
        return ResponseEntity.ok(
                ApiResponse.<CategoryResponse>builder()
                        .success(true)
                        .message("Orden de la categoría actualizado exitosamente")
                        .data(updated)
                        .build()
        );
    }

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Eliminar una categoría si no tiene productos asociados (solo OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @PathVariable UUID tenantId,
            @PathVariable UUID categoryId
    ) {
        categoryService.delete(tenantId, categoryId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Categoría eliminada exitosamente")
                        .build()
        );
    }
}
