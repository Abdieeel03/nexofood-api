package lat.nexofood.api.modules.payment.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.payment.application.service.OrderPaymentService;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lat.nexofood.api.modules.payment.web.dto.response.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
@RequiredArgsConstructor
@Tag(name = "Payment - Backoffice", description = "Consulta administrativa de pagos del restaurante")
public class TenantPaymentController {

    private final OrderPaymentService orderPaymentService;

    @GetMapping("/payments")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Listar pagos paginados del restaurante con filtro opcional por estado (OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> getTenantPayments(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) PaymentStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        PageResponse<PaymentResponse> response = orderPaymentService.findByTenantPaginated(tenantId, status, pageable);
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<PaymentResponse>>builder()
                        .success(true)
                        .message("Pagos del restaurante obtenidos exitosamente")
                        .data(response)
                        .build()
        );
    }

    @GetMapping("/orders/{orderId}/payment")
    @PreAuthorize("@tenantAccess.isMember(#tenantId)")
    @Operation(summary = "Obtener el detalle del pago asociado a una orden del restaurante (Miembro)")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrder(
            @PathVariable UUID tenantId,
            @PathVariable UUID orderId
    ) {
        PaymentResponse response = orderPaymentService.findByOrderIdAndTenant(orderId, tenantId);
        return ResponseEntity.ok(
                ApiResponse.<PaymentResponse>builder()
                        .success(true)
                        .message("Pago de la orden obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }
}
