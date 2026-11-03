package lat.nexofood.api.modules.cart.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lat.nexofood.api.common.exception.ForbiddenException;
import lat.nexofood.api.common.exception.GlobalExceptionHandler;
import lat.nexofood.api.modules.cart.application.service.CartService;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemRequest;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemUpdateRequest;
import lat.nexofood.api.modules.cart.web.dto.request.CartNotesRequest;
import lat.nexofood.api.modules.cart.web.dto.response.CartItemResponse;
import lat.nexofood.api.modules.cart.web.dto.response.CartResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    @Mock
    private CartService cartService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        CartController cartController = new CartController(cartService);
        mockMvc = MockMvcBuilders.standaloneSetup(cartController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/store/{slug}/cart: retorna el carrito del cliente autenticado (200 OK)")
    void getMyCartReturns200() throws Exception {
        UUID cartId = UUID.randomUUID();
        when(cartService.getMyCart("burger-bros")).thenReturn(
                CartResponse.builder()
                        .id(cartId)
                        .tenantSlug("burger-bros")
                        .total(new BigDecimal("50.00"))
                        .items(List.of())
                        .hasPriceChanges(false)
                        .hasUnavailableItems(false)
                        .warnings(List.of())
                        .build()
        );

        mockMvc.perform(get("/api/v1/store/burger-bros/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(cartId.toString()))
                .andExpect(jsonPath("$.data.tenantSlug").value("burger-bros"));
    }

    @Test
    @DisplayName("GET /api/v1/store/{slug}/cart: retorna 403 Forbidden si el cliente está bloqueado en la tienda")
    void getMyCartReturns403WhenCustomerBlocked() throws Exception {
        when(cartService.getMyCart("burger-bros")).thenThrow(
                new ForbiddenException("Tu cuenta ha sido bloqueada por este comercio.")
        );

        mockMvc.perform(get("/api/v1/store/burger-bros/cart"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("POST /api/v1/store/{slug}/cart/items: valida cantidad mínima y agrega producto al carrito")
    void addItemValidatesDtoAndAddsItem() throws Exception {
        // Cantidad inválida (0) -> 400 Bad Request
        CartItemRequest invalidRequest = CartItemRequest.builder()
                .productId(UUID.randomUUID())
                .quantity(0)
                .build();

        mockMvc.perform(post("/api/v1/store/burger-bros/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        // Petición válida -> 200 OK
        UUID productId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        CartItemRequest validRequest = CartItemRequest.builder()
                .productId(productId)
                .quantity(2)
                .notes("Sin mayonesa")
                .build();

        when(cartService.addItem(eq("burger-bros"), any())).thenReturn(
                CartResponse.builder()
                        .tenantSlug("burger-bros")
                        .total(new BigDecimal("50.00"))
                        .items(List.of(
                                CartItemResponse.builder()
                                        .id(itemId)
                                        .productId(productId)
                                        .productName("Burger Royal")
                                        .quantity(2)
                                        .unitPrice(new BigDecimal("25.00"))
                                        .subtotal(new BigDecimal("50.00"))
                                        .notes("Sin mayonesa")
                                        .build()
                        ))
                        .build()
        );

        mockMvc.perform(post("/api/v1/store/burger-bros/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].productName").value("Burger Royal"))
                .andExpect(jsonPath("$.data.total").value(50.00));
    }

    @Test
    @DisplayName("PATCH /items/{itemId}, PATCH /notes, DELETE /items/{itemId} y DELETE /cart responden 200 OK")
    void updateAndDeleteEndpointsReturn200() throws Exception {
        UUID itemId = UUID.randomUUID();

        when(cartService.updateItem(eq("burger-bros"), eq(itemId), any())).thenReturn(
                CartResponse.builder().tenantSlug("burger-bros").total(new BigDecimal("75.00")).items(List.of()).build()
        );
        when(cartService.updateCartNotes(eq("burger-bros"), any())).thenReturn(
                CartResponse.builder().tenantSlug("burger-bros").notes("Sin cubiertos").total(new BigDecimal("75.00")).items(List.of()).build()
        );
        when(cartService.removeItem("burger-bros", itemId)).thenReturn(
                CartResponse.builder().tenantSlug("burger-bros").total(BigDecimal.ZERO).items(List.of()).build()
        );
        when(cartService.clearCart("burger-bros")).thenReturn(
                CartResponse.builder().tenantSlug("burger-bros").total(BigDecimal.ZERO).items(List.of()).build()
        );

        mockMvc.perform(patch("/api/v1/store/burger-bros/cart/items/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CartItemUpdateRequest.builder().quantity(3).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(75.00));

        mockMvc.perform(patch("/api/v1/store/burger-bros/cart/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CartNotesRequest.builder().notes("Sin cubiertos").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notes").value("Sin cubiertos"));

        mockMvc.perform(delete("/api/v1/store/burger-bros/cart/items/" + itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/api/v1/store/burger-bros/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
