package lat.nexofood.api.modules.order.domain;

import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.order.web.dto.response.OrderResponse;
import lat.nexofood.api.modules.order.web.mapper.OrderItemMapper;
import lat.nexofood.api.modules.order.web.mapper.OrderMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OrderTest {

    @Test
    @DisplayName("Should create Order correctly with TenantCustomer relationship")
    void shouldCreateOrderCorrectly() {
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).name("Pizzeria").build();
        User user = User.builder()
                .id(UUID.randomUUID())
                .fullName("Carlos Mendoza")
                .email("carlos@example.com")
                .phone("+51987654321")
                .build();
        TenantCustomer customer = TenantCustomer.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .user(user)
                .build();
        User deliveryStaff = User.builder().id(UUID.randomUUID()).fullName("Repartidor").build();

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .customer(customer)
                .deliveryStaff(deliveryStaff)
                .orderNumber("ORD-001")
                .deliveryType(DeliveryType.DELIVERY)
                .status(OrderStatus.PENDIENTE)
                .deliveryAddress("Av. Principal 123")
                .subtotal(new BigDecimal("50.00"))
                .deliveryFee(new BigDecimal("5.00"))
                .total(new BigDecimal("55.00"))
                .build();

        assertEquals(tenant, order.getTenant());
        assertEquals(customer, order.getCustomer());
        assertEquals(user, order.getCustomer().getUser());
        assertEquals(deliveryStaff, order.getDeliveryStaff());
        assertEquals("ORD-001", order.getOrderNumber());
        assertEquals(new BigDecimal("55.00"), order.getTotal());
    }

    @Test
    @DisplayName("Should map Order to OrderResponse correctly including TenantCustomer details")
    void shouldMapOrderToResponseCorrectly() {
        OrderItemMapper orderItemMapper = new OrderItemMapper();
        OrderMapper orderMapper = new OrderMapper(orderItemMapper);

        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).name("Pizzeria").build();
        User user = User.builder()
                .id(UUID.randomUUID())
                .fullName("Carlos Mendoza")
                .email("carlos@example.com")
                .phone("+51987654321")
                .build();
        TenantCustomer customer = TenantCustomer.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .user(user)
                .build();

        ProductPrice price = ProductPrice.builder()
                .name("Precio Base")
                .price(new BigDecimal("25.00"))
                .isBase(true)
                .build();

        Product product = Product.builder()
                .id(UUID.randomUUID())
                .name("Pizza Pepperoni")
                .prices(new ArrayList<>(List.of(price)))
                .build();

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .productName(product.getName())
                .unitPrice(new BigDecimal("25.00"))
                .quantity(2)
                .subtotal(new BigDecimal("50.00"))
                .build();

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .customer(customer)
                .orderNumber("ORD-100")
                .deliveryType(DeliveryType.TAKEAWAY)
                .status(OrderStatus.PENDIENTE)
                .subtotal(new BigDecimal("50.00"))
                .taxTotal(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .total(new BigDecimal("50.00"))
                .items(new ArrayList<>(List.of(item)))
                .build();

        item.setOrder(order);

        OrderResponse response = orderMapper.toResponse(order);

        assertNotNull(response);
        assertEquals(order.getId(), response.id());
        assertEquals(tenant.getId(), response.tenantId());
        assertEquals(customer.getId(), response.customerId());
        assertEquals(user.getId(), response.customerUserId());
        assertEquals("Carlos Mendoza", response.customerFullName());
        assertEquals("carlos@example.com", response.customerEmail());
        assertEquals("+51987654321", response.customerPhone());
        assertEquals("ORD-100", response.orderNumber());
        assertEquals(DeliveryType.TAKEAWAY, response.deliveryType());
        assertEquals(OrderStatus.PENDIENTE, response.status());
        assertEquals(new BigDecimal("50.00"), response.total());
        assertEquals(1, response.items().size());
        assertEquals("Pizza Pepperoni", response.items().getFirst().productName());
    }
}
