package lat.nexofood.api.modules.payment.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lat.nexofood.api.modules.payment.application.service.MercadoPagoOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/mercadopago/oauth")
@RequiredArgsConstructor
@Tag(name = "Payment - Mercado Pago OAuth", description = "Callback público de autorización OAuth de Mercado Pago")
public class MercadoPagoOAuthCallbackController {

    private final MercadoPagoOAuthService oauthService;

    @GetMapping("/callback")
    @Operation(summary = "Callback OAuth de Mercado Pago (valida state firmado, guarda credenciales cifradas y redirige al frontend)")
    public ResponseEntity<Void> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error
    ) {
        String redirectUrl = oauthService.handleCallback(code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(redirectUrl).toString())
                .build();
    }
}
