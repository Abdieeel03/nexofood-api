package lat.nexofood.api.modules.cart.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.cart.application.service.CartService;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemRequest;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemUpdateRequest;
import lat.nexofood.api.modules.cart.web.dto.request.CartNotesRequest;
import lat.nexofood.api.modules.cart.web.dto.response.CartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/store/{slug}/cart")
@RequiredArgsConstructor
@Tag(name = "Storefront - Cart", description = "Gestión del carrito de compras del cliente autenticado por tienda")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Obtener mi carrito en la tienda (recalcula precios vigentes y verifica disponibilidad)")
    public ResponseEntity<ApiResponse<CartResponse>> getMyCart(@PathVariable String slug) {
        CartResponse response = cartService.getMyCart(slug);
        return ResponseEntity.ok(
                ApiResponse.<CartResponse>builder()
                        .success(true)
                        .message("Carrito obtenido exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PostMapping("/items")
    @Operation(summary = "Agregar un producto al carrito de la tienda (fusiona cantidad si ya existe)")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @PathVariable String slug,
            @Valid @RequestBody CartItemRequest request
    ) {
        CartResponse response = cartService.addItem(slug, request);
        return ResponseEntity.ok(
                ApiResponse.<CartResponse>builder()
                        .success(true)
                        .message("Producto agregado al carrito exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PatchMapping("/items/{itemId}")
    @Operation(summary = "Actualizar cantidad y/o notas de un ítem del carrito")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @PathVariable String slug,
            @PathVariable UUID itemId,
            @Valid @RequestBody CartItemUpdateRequest request
    ) {
        CartResponse response = cartService.updateItem(slug, itemId, request);
        return ResponseEntity.ok(
                ApiResponse.<CartResponse>builder()
                        .success(true)
                        .message("Ítem del carrito actualizado exitosamente")
                        .data(response)
                        .build()
        );
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Eliminar un ítem del carrito")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @PathVariable String slug,
            @PathVariable UUID itemId
    ) {
        CartResponse response = cartService.removeItem(slug, itemId);
        return ResponseEntity.ok(
                ApiResponse.<CartResponse>builder()
                        .success(true)
                        .message("Ítem eliminado del carrito exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PatchMapping("/notes")
    @Operation(summary = "Actualizar las notas generales del carrito")
    public ResponseEntity<ApiResponse<CartResponse>> updateCartNotes(
            @PathVariable String slug,
            @Valid @RequestBody CartNotesRequest request
    ) {
        CartResponse response = cartService.updateCartNotes(slug, request);
        return ResponseEntity.ok(
                ApiResponse.<CartResponse>builder()
                        .success(true)
                        .message("Notas del carrito actualizadas exitosamente")
                        .data(response)
                        .build()
        );
    }

    @DeleteMapping
    @Operation(summary = "Vaciar todos los ítems del carrito de la tienda")
    public ResponseEntity<ApiResponse<CartResponse>> clearCart(@PathVariable String slug) {
        CartResponse response = cartService.clearCart(slug);
        return ResponseEntity.ok(
                ApiResponse.<CartResponse>builder()
                        .success(true)
                        .message("Carrito vaciado exitosamente")
                        .data(response)
                        .build()
        );
    }
}
