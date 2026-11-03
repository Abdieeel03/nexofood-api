package lat.nexofood.api.modules.cart.domain;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemRequest;
import lat.nexofood.api.modules.cart.web.dto.response.CartItemResponse;
import lat.nexofood.api.modules.cart.web.dto.response.CartResponse;
import lat.nexofood.api.modules.cart.web.mapper.CartItemMapper;
import lat.nexofood.api.modules.cart.web.mapper.CartMapper;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.domain.TenantCustomerId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CartTest {

    @Test
    @DisplayName("Should create Cart and CartItem correctly")
    void shouldCreateCartAndCartItemCorrectly() {
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).name("Restaurant").build();
        User user = User.builder().id(UUID.randomUUID()).fullName("John Doe").build();
        TenantCustomer customer = TenantCustomer.builder()
                .id(new TenantCustomerId(tenant.getId(), user.getId()))
                .tenant(tenant)
                .user(user)
                .build();

        ProductPrice price1 = ProductPrice.builder()
                .name("Precio Base")
                .price(new BigDecimal("15.50"))
                .isBase(true)
                .build();

        Product product1 = Product.builder()
                .id(UUID.randomUUID())
                .name("Pizza Margherita")
                .prices(new ArrayList<>(List.of(price1)))
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

        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).name("Burger Joint").slug("burger-joint").build();
        User user = User.builder().id(UUID.randomUUID()).fullName("Jane Doe").build();
        TenantCustomer customer = TenantCustomer.builder()
                .id(new TenantCustomerId(tenant.getId(), user.getId()))
                .tenant(tenant)
                .user(user)
                .build();

        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .customer(customer)
                .notes("No plastic cutlery")
                .total(new BigDecimal("20.00"))
                .build();

        ProductPrice price = ProductPrice.builder()
                .name("Precio Base")
                .price(new BigDecimal("10.00"))
                .isBase(true)
                .build();

        Product product = Product.builder()
                .id(UUID.randomUUID())
                .name("Cheeseburger")
                .isAvailable(true)
                .prices(new ArrayList<>(List.of(price)))
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
        assertEquals("burger-joint", response.tenantSlug());
        assertEquals(user.getId(), response.customerId());
        assertEquals(user.getId(), response.customerUserId());
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
        assertTrue(itemResponse.productAvailable());
    }

    @Test
    @DisplayName("CartItem domain: increaseQuantity, updateQuantity, repriceTo and calculateSubtotal")
    void cartItemDomainMethodsWorkCorrectly() {
        CartItem item = CartItem.builder()
                .id(UUID.randomUUID())
                .quantity(2)
                .unitPrice(new BigDecimal("12.50"))
                .build();

        assertEquals(new BigDecimal("25.00"), item.calculateSubtotal());

        item.increaseQuantity(3);
        assertEquals(5, item.getQuantity());
        assertEquals(new BigDecimal("62.50"), item.getSubtotal());

        item.updateQuantity(4);
        assertEquals(4, item.getQuantity());
        assertEquals(new BigDecimal("50.00"), item.getSubtotal());

        item.repriceTo(new BigDecimal("10.00"));
        assertEquals(new BigDecimal("10.00"), item.getUnitPrice());
        assertEquals(new BigDecimal("40.00"), item.getSubtotal());

        assertThrows(BadRequestException.class, () -> item.updateQuantity(0));
        assertThrows(BadRequestException.class, () -> item.increaseQuantity(-1));
        assertThrows(BadRequestException.class, () -> item.repriceTo(new BigDecimal("-5.00")));
    }

    @Test
    @DisplayName("Cart domain: addItem merges duplicate products, removeItem, clear and calculateTotal")
    void cartDomainMethodsMergeRemoveAndClearItems() {
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).name("Pizzeria").build();
        Cart cart = Cart.builder().id(UUID.randomUUID()).tenant(tenant).build();

        Product pizza = Product.builder().id(UUID.randomUUID()).name("Pepperoni").build();
        Product drink = Product.builder().id(UUID.randomUUID()).name("Inca Kola").build();

        CartItem first = cart.addItem(pizza, 2, new BigDecimal("25.00"), "Sin aceitunas");
        first.setId(UUID.randomUUID());
        assertEquals(1, cart.getItems().size());
        assertEquals(new BigDecimal("50.00"), cart.getTotal());

        // Agregar el mismo producto fusiona cantidad y actualiza notas/precio
        CartItem merged = cart.addItem(pizza, 1, new BigDecimal("24.00"), "Bien dorada");
        assertEquals(1, cart.getItems().size());
        assertEquals(3, merged.getQuantity());
        assertEquals(new BigDecimal("24.00"), merged.getUnitPrice());
        assertEquals(new BigDecimal("72.00"), merged.getSubtotal());
        assertEquals("Bien dorada", merged.getNotes());
        assertEquals(new BigDecimal("72.00"), cart.getTotal());

        // Agregar segundo producto distinto
        CartItem second = cart.addItem(drink, 2, new BigDecimal("6.50"), null);
        second.setId(UUID.randomUUID());
        assertEquals(2, cart.getItems().size());
        assertEquals(new BigDecimal("85.00"), cart.getTotal());

        // Eliminar el primer ítem
        assertTrue(cart.removeItem(first.getId()));
        assertEquals(1, cart.getItems().size());
        assertEquals(new BigDecimal("13.00"), cart.getTotal());

        // Vaciar carrito
        cart.clear();
        assertTrue(cart.getItems().isEmpty());
        assertEquals(new BigDecimal("0.00"), cart.getTotal());
    }
}
