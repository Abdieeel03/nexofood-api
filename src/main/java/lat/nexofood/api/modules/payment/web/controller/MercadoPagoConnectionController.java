package lat.nexofood.api.modules.payment.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.payment.application.service.MercadoPagoOAuthService;
import lat.nexofood.api.modules.payment.web.dto.response.MercadoPagoConnectResponse;
import lat.nexofood.api.modules.payment.web.dto.response.MercadoPagoStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/mercadopago")
@RequiredArgsConstructor
@Tag(name = "Payment - Mercado Pago OAuth", description = "Vinculación de la cuenta de Mercado Pago del restaurante para cobro directo")
public class MercadoPagoConnectionController {

    private final MercadoPagoOAuthService oauthService;

    @GetMapping("/connect")
    @PreAuthorize("@tenantAccess.isOwner(#tenantId)")
    @Operation(summary = "Generar URL de autorización OAuth de Mercado Pago para vincular la cuenta del restaurante (solo OWNER)")
    public ResponseEntity<ApiResponse<MercadoPagoConnectResponse>> getConnectUrl(@PathVariable UUID tenantId) {
        MercadoPagoConnectResponse response = oauthService.buildAuthorizationUrl(tenantId);
        return ResponseEntity.ok(
                ApiResponse.<MercadoPagoConnectResponse>builder()
                        .success(true)
                        .message("URL de autorización de Mercado Pago generada exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/status")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Consultar el estado de conexión de Mercado Pago del restaurante (OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<MercadoPagoStatusResponse>> getStatus(@PathVariable UUID tenantId) {
        MercadoPagoStatusResponse response = oauthService.getStatus(tenantId);
        return ResponseEntity.ok(
                ApiResponse.<MercadoPagoStatusResponse>builder()
                        .success(true)
                        .message("Estado de conexión de Mercado Pago obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }

    @DeleteMapping
    @PreAuthorize("@tenantAccess.isOwner(#tenantId)")
    @Operation(summary = "Desvincular la cuenta de Mercado Pago del restaurante (solo OWNER)")
    public ResponseEntity<ApiResponse<Void>> disconnect(@PathVariable UUID tenantId) {
        oauthService.disconnect(tenantId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Cuenta de Mercado Pago desvinculada exitosamente")
                        .build()
        );
    }
}
