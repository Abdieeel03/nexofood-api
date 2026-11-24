package lat.nexofood.api.modules.order.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lat.nexofood.api.common.audit.SecurityAuditLogger;
import lat.nexofood.api.common.exception.GlobalExceptionHandler;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.common.util.GeoUtils;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.order.application.service.DefaultPaymentGuard;
import lat.nexofood.api.modules.order.application.service.KitchenOrderServiceImpl;
import lat.nexofood.api.modules.order.application.service.OrderServiceImpl;
import lat.nexofood.api.modules.order.application.service.OrderStateMachine;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderItem;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.infrastructure.repository.OrderRepository;
import lat.nexofood.api.modules.order.web.dto.request.KitchenOrderStatusRequest;
import lat.nexofood.api.modules.order.web.mapper.KitchenOrderMapper;
import lat.nexofood.api.modules.order.web.mapper.OrderItemMapper;
import lat.nexofood.api.modules.order.web.mapper.OrderMapper;
import lat.nexofood.api.modules.payment.domain.Payment;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lat.nexofood.api.modules.payment.infrastructure.repository.PaymentRepository;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.domain.TenantCustomerId;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
class KitchenControllerTest {

    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "subtotal",
            "taxTotal",
            "deliveryFee",
            "total",
            "unitPrice",
            "taxAmount",
            "ruc",
            "customerEmail",
            "customerPhone",
            "deliveryAddress",
            "deliveryLatitude",
            "deliveryLongitude",
            "customerFullName"
    );

    private OrderRepository orderRepository;
    private PaymentRepository paymentRepository;
    private TenantAccessService tenantAccessService;
    private CurrentUserProvider currentUserProvider;
    private KitchenController proxiedKitchenController;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        tenantAccessService = mock(TenantAccessService.class);
        currentUserProvider = mock(CurrentUserProvider.class);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        OrderItemMapper orderItemMapper = new OrderItemMapper(null, null);
        OrderMapper orderMapper = new OrderMapper(orderItemMapper);
        OrderServiceImpl orderService = new OrderServiceImpl(
                orderRepository,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                orderMapper,
                orderItemMapper,
                null,
                null,
                new OrderStateMachine(),
                new DefaultPaymentGuard(paymentRepository),
                tenantAccessService,
                currentUserProvider,
                null,
                null,
                new SecurityAuditLogger()
        );

        KitchenOrderServiceImpl kitchenOrderService = new KitchenOrderServiceImpl(
                orderRepository,
                paymentRepository,
                orderService,
                new KitchenOrderMapper()
        );

        StaticApplicationContext appContext = new StaticApplicationContext();
        appContext.getBeanFactory().registerSingleton("tenantAccess", tenantAccessService);
        appContext.refresh();

        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setApplicationContext(appContext);

        PreAuthorizeAuthorizationManager authorizationManager = new PreAuthorizeAuthorizationManager();
        authorizationManager.setExpressionHandler(expressionHandler);

        AuthorizationManagerBeforeMethodInterceptor interceptor =
                AuthorizationManagerBeforeMethodInterceptor.preAuthorize(authorizationManager);

        AspectJProxyFactory factory = new AspectJProxyFactory(new KitchenController(kitchenOrderService));
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        proxiedKitchenController = factory.getProxy();

        mockMvc = MockMvcBuilders.standaloneSetup(proxiedKitchenController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        tenantId = UUID.randomUUID();
        tenant = Tenant.builder()
                .id(tenantId)
                .name("Burger Bros")
                .slug("burger-bros")
                .address("Av. Larco 123")
                .phone("+5114445566")
                .location(GeoUtils.createPoint(-12.1191, -77.0282))
                .build();
    }

    @Test
    @DisplayName("KitchenController RBAC: solo permite OWNER, ADMIN o COCINERO; rechaza REPARTIDOR/no-miembros")
    void kitchenEndpointsEnforceRoleAuthorization() throws Exception {
        UUID orderId = UUID.randomUUID();

        when(tenantAccessService.hasAnyRole(
                tenantId,
                TenantStaffRole.OWNER,
                TenantStaffRole.ADMIN,
                TenantStaffRole.COCINERO
        )).thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
                proxiedKitchenController.getKitchenOrders(tenantId, null)
        );
        assertThrows(AccessDeniedException.class, () ->
                proxiedKitchenController.getKitchenOrderById(tenantId, orderId)
        );
        assertThrows(AccessDeniedException.class, () ->
                proxiedKitchenController.updateKitchenOrderStatus(
                        tenantId, orderId, new KitchenOrderStatusRequest(OrderStatus.EN_PREPARACION)
                )
        );

        when(tenantAccessService.hasAnyRole(
                tenantId,
                TenantStaffRole.OWNER,
                TenantStaffRole.ADMIN,
                TenantStaffRole.COCINERO
        )).thenReturn(true);
        when(orderRepository.findKitchenQueueOrders(
                tenantId,
                OrderStatus.PENDIENTE,
                OrderStatus.EN_PREPARACION,
                PaymentStatus.APPROVED
        )).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/kitchen/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /kitchen/orders: filtra PENDIENTE pagados + EN_PREPARACION FIFO, rechaza otros estados y omite campos financieros/PII")
    void getKitchenOrdersFiltersAndStripsSensitiveFields() throws Exception {
        when(tenantAccessService.hasAnyRole(
                tenantId,
                TenantStaffRole.OWNER,
                TenantStaffRole.ADMIN,
                TenantStaffRole.COCINERO
        )).thenReturn(true);

        Order paidPending = buildOrder("ORD-000010", OrderStatus.PENDIENTE, "  Juan Carlos Pérez ", OffsetDateTime.now().minusMinutes(5));
        Order inPrep = buildOrder("ORD-000011", OrderStatus.EN_PREPARACION, "   ", OffsetDateTime.now().minusMinutes(2));

        when(orderRepository.findKitchenQueueOrders(
                tenantId,
                OrderStatus.PENDIENTE,
                OrderStatus.EN_PREPARACION,
                PaymentStatus.APPROVED
        )).thenReturn(List.of(paidPending, inPrep));

        MvcResult listResult = mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/kitchen/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].orderNumber").value("ORD-000010"))
                .andExpect(jsonPath("$.data[0].customerFirstName").value("Juan"))
                .andExpect(jsonPath("$.data[0].items[0].productName").value("Burger Clásica"))
                .andExpect(jsonPath("$.data[0].items[0].quantity").value(2))
                .andExpect(jsonPath("$.data[1].orderNumber").value("ORD-000011"))
                .andExpect(jsonPath("$.data[1].customerFirstName").value("Comensal"))
                .andReturn();

        assertNoForbiddenJsonFields(listResult.getResponse().getContentAsString());

        when(orderRepository.findPaidOrdersByTenantIdAndStatusOrderByCreatedAtAsc(
                tenantId,
                OrderStatus.PENDIENTE,
                PaymentStatus.APPROVED
        )).thenReturn(List.of(paidPending));

        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/kitchen/orders").param("status", "PENDIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("PENDIENTE"));

        when(orderRepository.findAllByTenantIdAndStatusOrderByCreatedAtAsc(tenantId, OrderStatus.EN_PREPARACION))
                .thenReturn(List.of(inPrep));

        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/kitchen/orders").param("status", "EN_PREPARACION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("EN_PREPARACION"));

        // Estados fuera de cocina devuelven 400 BadRequest
        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/kitchen/orders").param("status", "ENTREGADO"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/kitchen/orders").param("status", "LISTO_PARA_ENTREGA"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /kitchen/orders/{orderId} y PATCH /status: valida pago aprobado, transiciones permitidas y JSON sin datos sensibles")
    void getOrderByIdAndPatchStatusWorkWithStrictGuards() throws Exception {
        when(tenantAccessService.hasAnyRole(
                tenantId,
                TenantStaffRole.OWNER,
                TenantStaffRole.ADMIN,
                TenantStaffRole.COCINERO
        )).thenReturn(true);
        when(tenantAccessService.isSuperAdmin()).thenReturn(false);
        when(tenantAccessService.getCurrentUserRoleInTenant(tenantId))
                .thenReturn(Optional.of(TenantStaffRole.COCINERO));
        when(currentUserProvider.getCurrentUserId()).thenReturn(UUID.randomUUID());

        Order unpaidPending = buildOrder("ORD-000020", OrderStatus.PENDIENTE, "Ana Gómez", OffsetDateTime.now());
        when(orderRepository.findByIdAndTenantId(unpaidPending.getId(), tenantId)).thenReturn(Optional.of(unpaidPending));
        when(paymentRepository.findByOrderId(unpaidPending.getId())).thenReturn(Optional.of(
                Payment.builder().id(UUID.randomUUID()).order(unpaidPending).status(PaymentStatus.PENDING).build()
        ));

        // PENDIENTE sin pago APPROVED -> 403 Forbidden en GET /kitchen/orders/{id}
        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/kitchen/orders/" + unpaidPending.getId()))
                .andExpect(status().isForbidden());

        // Con pago APPROVED -> 200 OK en GET y puede pasar a EN_PREPARACION en PATCH
        Payment approvedPayment = Payment.builder().id(UUID.randomUUID()).order(unpaidPending).status(PaymentStatus.APPROVED).build();
        when(paymentRepository.findByOrderId(unpaidPending.getId())).thenReturn(Optional.of(approvedPayment));
        when(orderRepository.findByIdAndTenantIdForUpdate(unpaidPending.getId(), tenantId)).thenReturn(Optional.of(unpaidPending));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        MvcResult detailResult = mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/kitchen/orders/" + unpaidPending.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerFirstName").value("Ana"))
                .andReturn();
        assertNoForbiddenJsonFields(detailResult.getResponse().getContentAsString());

        MvcResult patchResult = mockMvc.perform(patch("/api/v1/tenants/" + tenantId + "/kitchen/orders/" + unpaidPending.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KitchenOrderStatusRequest(OrderStatus.EN_PREPARACION))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EN_PREPARACION"))
                .andReturn();
        assertNoForbiddenJsonFields(patchResult.getResponse().getContentAsString());
        verify(orderRepository).save(unpaidPending);

        // PATCH con estado no permitido para cocina -> 400 BadRequest
        mockMvc.perform(patch("/api/v1/tenants/" + tenantId + "/kitchen/orders/" + unpaidPending.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KitchenOrderStatusRequest(OrderStatus.EN_CAMINO))))
                .andExpect(status().isBadRequest());

        // GET de orden ya ENTREGADO -> 403 Forbidden
        Order delivered = buildOrder("ORD-000021", OrderStatus.ENTREGADO, "Luis Salas", OffsetDateTime.now());
        when(orderRepository.findByIdAndTenantId(delivered.getId(), tenantId)).thenReturn(Optional.of(delivered));
        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/kitchen/orders/" + delivered.getId()))
                .andExpect(status().isForbidden());
    }

    private Order buildOrder(String orderNumber, OrderStatus status, String fullName, OffsetDateTime createdAt) {
        UUID customerId = UUID.randomUUID();
        User customerUser = User.builder()
                .id(customerId)
                .fullName(fullName)
                .email("cliente@nexofood.lat")
                .phone("+51999111222")
                .build();
        TenantCustomer customer = TenantCustomer.builder()
                .id(new TenantCustomerId(tenantId, customerId))
                .tenant(tenant)
                .user(customerUser)
                .build();
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name("Burger Clásica")
                .build();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .customer(customer)
                .customerId(customerId)
                .orderNumber(orderNumber)
                .ruc("20123456789")
                .deliveryType(DeliveryType.DELIVERY)
                .status(status)
                .deliveryAddress("Av. Pardo 450, Miraflores")
                .deliveryLocation(GeoUtils.createPoint(-12.1210, -77.0300))
                .subtotal(new BigDecimal("50.00"))
                .taxTotal(new BigDecimal("9.00"))
                .deliveryFee(new BigDecimal("6.50"))
                .total(new BigDecimal("65.50"))
                .notes("Sin ají")
                .build();
        order.setCreatedAt(createdAt);
        order.setUpdatedAt(createdAt);

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .tenant(tenant)
                .product(product)
                .productName("Burger Clásica")
                .unitPrice(new BigDecimal("25.00"))
                .quantity(2)
                .subtotal(new BigDecimal("50.00"))
                .taxAmount(new BigDecimal("9.00"))
                .notes("Término medio")
                .build();
        order.setItems(List.of(item));
        return order;
    }

    private void assertNoForbiddenJsonFields(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        assertTreeHasNoForbiddenFields(root);
    }

    private void assertTreeHasNoForbiddenFields(JsonNode node) {
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                assertFalse(FORBIDDEN_FIELDS.contains(fieldName), "Campo prohibido expuesto en JSON de cocina: " + fieldName);
                assertTreeHasNoForbiddenFields(node.get(fieldName));
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                assertTreeHasNoForbiddenFields(child);
            }
        }
    }
}
