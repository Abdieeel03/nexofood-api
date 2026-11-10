package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.common.exception.PaymentGatewayException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.infrastructure.repository.OrderRepository;
import lat.nexofood.api.modules.order.web.dto.response.OrderResponse;
import lat.nexofood.api.modules.order.web.mapper.OrderItemMapper;
import lat.nexofood.api.modules.order.web.mapper.OrderMapper;
import lat.nexofood.api.modules.payment.application.service.PaymentRefundService;
import lat.nexofood.api.modules.store.application.service.customer.CurrentCustomerResolver;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.domain.TenantCustomerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancelByCustomerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CurrentCustomerResolver currentCustomerResolver;
    @Mock
    private PaymentRefundService paymentRefundService;

    private OrderServiceImpl orderService;
    private Tenant tenant;
    private TenantCustomer customer;
    private UUID userId;

    @BeforeEach
    void setUp() {
        OrderItemMapper itemMapper = new OrderItemMapper(null, null);
        OrderMapper orderMapper = new OrderMapper(itemMapper);

        orderService = new OrderServiceImpl(
                orderRepository,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                orderMapper,
                itemMapper,
                currentCustomerResolver,
                null,
                new OrderStateMachine(),
                null,
                null,
                null,
                null,
                paymentRefundService,
                null
        );

        userId = UUID.randomUUID();
        tenant = Tenant.builder().id(UUID.randomUUID()).name("Burger Bros").slug("burger-bros").build();
        User user = User.builder().id(userId).email("cliente@nexofood.lat").build();
        customer = TenantCustomer.builder()
                .id(new TenantCustomerId(tenant.getId(), userId))
                .tenant(tenant)
                .user(user)
                .build();
    }

    @Test
    @DisplayName("cancelByCustomer: si la orden sigue en PENDIENTE, ejecuta el reembolso en MP y cambia el estado a CANCELADO")
    void cancelByCustomerInPendingRefundsAndCancelsOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .tenant(tenant)
                .customer(customer)
                .customerId(userId)
                .orderNumber("ORD-000010")
                .deliveryType(DeliveryType.DELIVERY)
                .status(OrderStatus.PENDIENTE)
                .total(new BigDecimal("45.00"))
                .items(List.of())
                .build();

        when(currentCustomerResolver.resolveBySlug("burger-bros")).thenReturn(customer);
        when(orderRepository.findByIdAndTenantIdAndCustomerUserIdForUpdate(orderId, tenant.getId(), userId))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse response = orderService.cancelByCustomer("burger-bros", orderId, "Pedí por error");

        assertEquals(OrderStatus.CANCELADO, response.status());
        verify(paymentRefundService).refundOrderPayment(order, "Pedí por error");
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("cancelByCustomer: si la orden ya pasó a EN_PREPARACION, rechaza con 409 Conflict y NO ejecuta reembolso")
    void cancelByCustomerRejectsOrderNotInPending() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .tenant(tenant)
                .customer(customer)
                .customerId(userId)
                .orderNumber("ORD-000011")
                .status(OrderStatus.EN_PREPARACION)
                .items(List.of())
                .build();

        when(currentCustomerResolver.resolveBySlug("burger-bros")).thenReturn(customer);
        when(orderRepository.findByIdAndTenantIdAndCustomerUserIdForUpdate(orderId, tenant.getId(), userId))
                .thenReturn(Optional.of(order));

        assertThrows(ResourceConflictException.class, () ->
                orderService.cancelByCustomer("burger-bros", orderId, "Quiero cancelar")
        );

        assertEquals(OrderStatus.EN_PREPARACION, order.getStatus());
        verify(paymentRefundService, never()).refundOrderPayment(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelByCustomer: si falla el reembolso en Mercado Pago, la orden se mantiene en PENDIENTE")
    void cancelByCustomerDoesNotCancelOrderWhenRefundFails() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .tenant(tenant)
                .customer(customer)
                .customerId(userId)
                .orderNumber("ORD-000012")
                .status(OrderStatus.PENDIENTE)
                .items(List.of())
                .build();

        when(currentCustomerResolver.resolveBySlug("burger-bros")).thenReturn(customer);
        when(orderRepository.findByIdAndTenantIdAndCustomerUserIdForUpdate(orderId, tenant.getId(), userId))
                .thenReturn(Optional.of(order));
        doThrow(new PaymentGatewayException("MP error")).when(paymentRefundService)
                .refundOrderPayment(eq(order), any());

        assertThrows(PaymentGatewayException.class, () ->
                orderService.cancelByCustomer("burger-bros", orderId, "Cancelando")
        );

        assertEquals(OrderStatus.PENDIENTE, order.getStatus());
        verify(orderRepository, never()).save(any());
    }
}
