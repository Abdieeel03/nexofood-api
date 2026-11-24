package lat.nexofood.api.modules.store.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.store.application.service.tenant.TenantService;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/admin/tenants", "/api/v1/admin/stores"})
@RequiredArgsConstructor
@PreAuthorize("@tenantAccess.isSuperAdmin()")
@Tag(name = "Admin Tenants", description = "Gestión administrativa global de restaurantes/tiendas (SUPERADMIN)")
public class AdminTenantController {

    private final TenantService tenantService;

    @GetMapping
    @Operation(summary = "Listar todas las tiendas/restaurantes registrados en la plataforma (Solo SUPERADMIN)")
    public ResponseEntity<ApiResponse<PageResponse<TenantResponse>>> getAllTenants(
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean isOpen,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<TenantResponse> response = tenantService.getAllTenants(isActive, isOpen, search, pageable);
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<TenantResponse>>builder()
                        .success(true)
                        .message("Tiendas obtenidas exitosamente")
                        .data(response)
                        .build()
        );
    }
}
