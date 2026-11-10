package lat.nexofood.api.modules.payment.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.payment.application.service.MercadoPagoOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mercadopago/oauth")
@RequiredArgsConstructor
@Tag(name = "Payment - Mercado Pago OAuth", description = "Callback público de autorización OAuth de Mercado Pago")
public class MercadoPagoOAuthCallbackController {

    private final MercadoPagoOAuthService oauthService;

    @GetMapping("/callback")
    @Operation(summary = "Callback OAuth de Mercado Pago (valida state firmado, guarda credenciales cifradas y redirige al frontend)")
    public ResponseEntity<?> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String acceptHeader
    ) {
        String redirectUrl = oauthService.handleCallback(code, state, error);
        boolean success = redirectUrl != null && redirectUrl.contains("mp=connected");

        // Si se invoca desde un cliente API como Bruno/Postman (que no pide text/html),
        // devolvemos 200 OK en JSON para evitar que siga el 302 hacia la ruta del frontend
        if (acceptHeader == null || !acceptHeader.contains("text/html")) {
            Map<String, Object> payload = Map.of(
                    "connected", success,
                    "redirectUrl", redirectUrl != null ? redirectUrl : ""
            );
            String message = success
                    ? "Cuenta de Mercado Pago vinculada exitosamente"
                    : "No se pudo completar la vinculación con Mercado Pago";
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                    .success(success)
                    .message(message)
                    .data(payload)
                    .build());
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(redirectUrl).toString())
                .build();
    }
}
