package lat.nexofood.api.modules.cart.domain;

import lat.nexofood.api.modules.auth.domain.User;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemRequest;
import lat.nexofood.api.modules.cart.web.dto.response.CartItemResponse;
import lat.nexofood.api.modules.cart.web.dto.response.CartResponse;
import lat.nexofood.api.modules.cart.web.mapper.CartItemMapper;
import lat.nexofood.api.modules.cart.web.mapper.CartMapper;
import lat.nexofood.api.modules.tenant.domain.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CartTest {

    @Test
    @DisplayName("Should create Cart and CartItem correctly")
    void shouldCreateCartAndCartItemCorrectly() {
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).name("Restaurant").build();
        User customer = User.builder().id(UUID.randomUUID()).fullName("John Doe").build();

        Product product1 = Product.builder()
                .id(UUID.randomUUID())
                .name("Pizza Margherita")
                .price(new BigDecimal("15.50"))
                .build();

        CartItem item1 = CartItem.builder()
                .id(UUID.randomUUID())
                .product(product1)
                .quantity(2)
                .unitPrice(new BigDecimal("15.50"))
                .subtotal(new BigDecimal("31.00"))
                .build();

        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .customer(customer)
                .total(new BigDecimal("31.00"))
                .build();

        item1.setCart(cart);
        cart.getItems().add(item1);

        assertEquals(1, cart.getItems().size());
        assertEquals(tenant, cart.getTenant());
        assertEquals(customer, cart.getCustomer());
        assertEquals(new BigDecimal("31.00"), cart.getTotal());
        assertEquals(product1, cart.getItems().getFirst().getProduct());
    }

    @Test
    @DisplayName("Should map Cart and CartItem to Response DTO correctly")
    void shouldMapCartAndCartItemToResponse() {
        CartItemMapper itemMapper = new CartItemMapper();
        CartMapper cartMapper = new CartMapper(itemMapper);

        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).name("Burger Joint").build();
        User customer = User.builder().id(UUID.randomUUID()).fullName("Jane Doe").build();

        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .customer(customer)
                .notes("No plastic cutlery")
                .total(new BigDecimal("20.00"))
                .build();

        Product product = Product.builder()
                .id(UUID.randomUUID())
                .name("Cheeseburger")
                .price(new BigDecimal("10.00"))
                .build();

        CartItemRequest request = CartItemRequest.builder()
                .productId(product.getId())
                .quantity(2)
                .notes("Well done")
                .build();

        CartItem cartItem = itemMapper.toEntity(request, product, cart);
        assertNotNull(cartItem);
        assertEquals(new BigDecimal("20.00"), cartItem.getSubtotal());

        cart.getItems().add(cartItem);

        CartResponse response = cartMapper.toResponse(cart);
        assertNotNull(response);
        assertEquals(cart.getId(), response.id());
        assertEquals(tenant.getId(), response.tenantId());
        assertEquals(customer.getId(), response.customerId());
        assertEquals("No plastic cutlery", response.notes());
        assertEquals(new BigDecimal("20.00"), response.total());
        assertEquals(1, response.items().size());

        CartItemResponse itemResponse = response.items().getFirst();
        assertEquals(product.getId(), itemResponse.productId());
        assertEquals("Cheeseburger", itemResponse.productName());
        assertEquals(2, itemResponse.quantity());
        assertEquals(new BigDecimal("10.00"), itemResponse.unitPrice());
        assertEquals(new BigDecimal("20.00"), itemResponse.subtotal());
        assertEquals("Well done", itemResponse.notes());
    }
}
