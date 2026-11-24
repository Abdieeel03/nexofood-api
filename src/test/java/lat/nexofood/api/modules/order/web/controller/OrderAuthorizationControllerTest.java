package lat.nexofood.api.modules.order.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lat.nexofood.api.common.exception.GlobalExceptionHandler;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.modules.order.application.service.CheckoutService;
import lat.nexofood.api.modules.order.application.service.OrderService;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.web.dto.request.OrderAssignStaffRequest;
import lat.nexofood.api.modules.order.web.dto.request.OrderCancelRequest;
import lat.nexofood.api.modules.order.web.dto.request.OrderCheckoutRequest;
import lat.nexofood.api.modules.order.web.dto.request.OrderStatusUpdateRequest;
import lat.nexofood.api.modules.order.web.dto.response.OrderResponse;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lat.nexofood.api.modules.payment.web.dto.response.CheckoutPaymentResponse;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
class OrderAuthorizationControllerTest {

    private OrderService orderService;
    private CheckoutService checkoutService;
    private TenantAccessService tenantAccessService;

    private OrderController proxiedOrderController;
    private StorefrontOrderController storefrontOrderController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        checkoutService = mock(CheckoutService.class);
        tenantAccessService = mock(TenantAccessService.class);

        StaticApplicationContext appContext = new StaticApplicationContext();
        appContext.getBeanFactory().registerSingleton("tenantAccess", tenantAccessService);
        appContext.refresh();

        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setApplicationContext(appContext);

        PreAuthorizeAuthorizationManager authorizationManager = new PreAuthorizeAuthorizationManager();
        authorizationManager.setExpressionHandler(expressionHandler);

        AuthorizationManagerBeforeMethodInterceptor interceptor =
                AuthorizationManagerBeforeMethodInterceptor.preAuthorize(authorizationManager);

        proxiedOrderController = createSecuredProxy(new OrderController(orderService), interceptor);
        storefrontOrderController = new StorefrontOrderController(checkoutService, orderService);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        proxiedOrderController,
                        storefrontOrderController
                )
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T> T createSecuredProxy(T target, AuthorizationManagerBeforeMethodInterceptor interceptor) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return (T) factory.getProxy();
    }

    @Test
    @DisplayName("StorefrontOrderController: permite checkout desde carrito (201 CheckoutPaymentResponse), cancelar en PENDIENTE, listar mis pedidos y ver detalle")
    void storefrontOrderEndpointsWorkCorrectly() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        CheckoutPaymentResponse checkoutPaymentResponse = CheckoutPaymentResponse.builder()
                .paymentId(paymentId)
                .mpOrderId("ORD-MP-001")
                .checkoutUrl("https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=001")
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("59.00"))
                .currency("PEN")
                .build();

        OrderResponse orderResponse = OrderResponse.builder()
                .id(orderId)
                .tenantSlug("burger-bros")
                .orderNumber("ORD-000001")
                .deliveryType(DeliveryType.TAKEAWAY)
                .status(OrderStatus.PENDIENTE)
                .total(new BigDecimal("59.00"))
                .items(List.of())
                .build();

        OrderResponse canceledOrderResponse = OrderResponse.builder()
                .id(orderId)
                .tenantSlug("burger-bros")
                .orderNumber("ORD-000001")
                .deliveryType(DeliveryType.TAKEAWAY)
                .status(OrderStatus.CANCELADO)
                .total(new BigDecimal("59.00"))
                .items(List.of())
                .build();

        when(checkoutService.createFromCart(eq("burger-bros"), any())).thenReturn(checkoutPaymentResponse);
        when(orderService.cancelByCustomer(eq("burger-bros"), eq(orderId), any())).thenReturn(canceledOrderResponse);
        when(orderService.getMyOrdersBySlug(eq("burger-bros"), any())).thenReturn(
                PageResponse.<OrderResponse>builder()
                        .content(List.of(orderResponse))
                        .page(0)
                        .size(20)
                        .totalElements(1)
                        .totalPages(1)
                        .first(true)
                        .last(true)
                        .build()
        );
        when(orderService.getMyOrderDetailBySlug("burger-bros", orderId)).thenReturn(orderResponse);

        OrderCheckoutRequest checkoutReq = OrderCheckoutRequest.builder()
                .deliveryType(DeliveryType.TAKEAWAY)
                .notes("Para llevar")
                .build();

        mockMvc.perform(post("/api/v1/store/burger-bros/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.data.checkoutUrl").value("https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=001"));

        mockMvc.perform(post("/api/v1/store/burger-bros/orders/" + orderId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderCancelRequest("Me equivoqué de producto"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELADO"));

        mockMvc.perform(get("/api/v1/store/burger-bros/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderNumber").value("ORD-000001"));

        mockMvc.perform(get("/api/v1/store/burger-bros/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId.toString()));
    }

    @Test
    @DisplayName("OrderController Backoffice: restringe todos los endpoints (/orders, /kds, /my-deliveries, /{id}, /{id}/status, /{id}/assign) solo a OWNER o ADMIN")
    void backofficeOrderControllerEnforcesOwnerOrAdminOnly() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        // Aunque el usuario sea miembro / COCINERO / REPARTIDOR, sin rol OWNER o ADMIN todos los endpoints backoffice son denegados
        when(tenantAccessService.isMember(tenantId)).thenReturn(true);
        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN, TenantStaffRole.COCINERO))
                .thenReturn(true);
        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN, TenantStaffRole.REPARTIDOR))
                .thenReturn(true);
        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
                proxiedOrderController.getOrders(tenantId, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 20))
        );
        assertThrows(AccessDeniedException.class, () ->
                proxiedOrderController.getKitchenOrders(tenantId)
        );
        assertThrows(AccessDeniedException.class, () ->
                proxiedOrderController.getMyAssignedDeliveries(tenantId, null)
        );
        assertThrows(AccessDeniedException.class, () ->
                proxiedOrderController.getOrderById(tenantId, orderId)
        );
        assertThrows(AccessDeniedException.class, () ->
                proxiedOrderController.updateOrderStatus(
                        tenantId, orderId, new OrderStatusUpdateRequest(OrderStatus.EN_PREPARACION)
                )
        );
        assertThrows(AccessDeniedException.class, () ->
                proxiedOrderController.assignDeliveryStaff(
                        tenantId, orderId, new OrderAssignStaffRequest(staffId)
                )
        );

        // Con rol OWNER o ADMIN todos los endpoints backoffice responden 200 OK
        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(true);
        when(orderService.findByTenantPaginated(eq(tenantId), any(), any(), any(), any(), any())).thenReturn(
                PageResponse.<OrderResponse>builder()
                        .content(List.of())
                        .page(0)
                        .size(20)
                        .totalElements(0)
                        .totalPages(0)
                        .first(true)
                        .last(true)
                        .build()
        );
        when(orderService.getKitchenOrders(tenantId)).thenReturn(List.of(
                OrderResponse.builder().id(orderId).orderNumber("ORD-000001").status(OrderStatus.EN_PREPARACION).build()
        ));
        when(orderService.getMyAssignedDeliveries(tenantId, null)).thenReturn(List.of());
        when(orderService.findByIdAndTenant(orderId, tenantId)).thenReturn(
                OrderResponse.builder().id(orderId).orderNumber("ORD-000001").status(OrderStatus.EN_PREPARACION).build()
        );
        when(orderService.updateStatus(tenantId, orderId, OrderStatus.LISTO_PARA_ENTREGA)).thenReturn(
                OrderResponse.builder().id(orderId).status(OrderStatus.LISTO_PARA_ENTREGA).build()
        );
        when(orderService.assignDeliveryStaff(tenantId, orderId, staffId)).thenReturn(
                OrderResponse.builder().id(orderId).deliveryStaffId(staffId).build()
        );

        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/orders/kds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("EN_PREPARACION"));

        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/orders/my-deliveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId.toString()));

        mockMvc.perform(patch("/api/v1/tenants/" + tenantId + "/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.LISTO_PARA_ENTREGA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LISTO_PARA_ENTREGA"));

        mockMvc.perform(patch("/api/v1/tenants/" + tenantId + "/orders/" + orderId + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderAssignStaffRequest(staffId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryStaffId").value(staffId.toString()));
    }
}
