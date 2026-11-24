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
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.order.application.service.DriverOrderServiceImpl;
import lat.nexofood.api.modules.order.application.service.OrderServiceImpl;
import lat.nexofood.api.modules.order.application.service.OrderStateMachine;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderItem;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.infrastructure.repository.OrderRepository;
import lat.nexofood.api.modules.order.web.dto.request.DriverOrderStatusRequest;
import lat.nexofood.api.modules.order.web.mapper.DriverOrderMapper;
import lat.nexofood.api.modules.order.web.mapper.OrderItemMapper;
import lat.nexofood.api.modules.order.web.mapper.OrderMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.domain.TenantCustomerId;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.tracking.application.config.TrackingProperties;
import lat.nexofood.api.modules.tracking.application.service.DeliveryTrackingServiceImpl;
import lat.nexofood.api.modules.tracking.application.service.HaversineEtaProvider;
import lat.nexofood.api.modules.tracking.domain.DeliveryLocationHistory;
import lat.nexofood.api.modules.tracking.domain.DeliveryTracking;
import lat.nexofood.api.modules.tracking.infrastructure.repository.DeliveryLocationHistoryRepository;
import lat.nexofood.api.modules.tracking.infrastructure.repository.DeliveryTrackingRepository;
import lat.nexofood.api.modules.tracking.web.dto.request.DriverLocationUpdateRequest;
import lat.nexofood.api.modules.tracking.web.mapper.DeliveryTrackingMapper;
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
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
class DriverControllerTest {

    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "subtotal",
            "taxTotal",
            "deliveryFee",
            "total",
            "unitPrice",
            "taxAmount",
            "ruc",
            "customerEmail"
    );

    private OrderRepository orderRepository;
    private DeliveryTrackingRepository trackingRepository;
    private DeliveryLocationHistoryRepository historyRepository;
    private TenantRepository tenantRepository;
    private TenantAccessService tenantAccessService;
    private CurrentUserProvider currentUserProvider;
    private DriverController proxiedDriverController;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID tenantId;
    private Tenant tenant;
    private User assignedDriver;
    private User otherDriver;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        trackingRepository = mock(DeliveryTrackingRepository.class);
        historyRepository = mock(DeliveryLocationHistoryRepository.class);
        tenantRepository = mock(TenantRepository.class);
        tenantAccessService = mock(TenantAccessService.class);
        currentUserProvider = mock(CurrentUserProvider.class);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        TrackingProperties trackingProperties = new TrackingProperties();
        HaversineEtaProvider etaProvider = new HaversineEtaProvider(trackingProperties, trackingRepository);
        DeliveryTrackingServiceImpl trackingService = new DeliveryTrackingServiceImpl(
                trackingRepository,
                historyRepository,
                orderRepository,
                tenantRepository,
                etaProvider,
                trackingProperties,
                new DeliveryTrackingMapper(trackingProperties),
                currentUserProvider,
                tenantAccessService,
                null
        );

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
                null,
                tenantAccessService,
                currentUserProvider,
                null,
                etaProvider,
                trackingService
        );

        DriverOrderServiceImpl driverOrderService = new DriverOrderServiceImpl(
                orderRepository,
                orderService,
                new DriverOrderMapper(),
                currentUserProvider,
                tenantAccessService
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

        AspectJProxyFactory factory = new AspectJProxyFactory(
                new DriverController(driverOrderService, trackingService)
        );
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        proxiedDriverController = factory.getProxy();

        mockMvc = MockMvcBuilders.standaloneSetup(proxiedDriverController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        tenantId = UUID.randomUUID();
        tenant = Tenant.builder()
                .id(tenantId)
                .name("Burger Bros")
                .slug("burger-bros")
                .address("Av. Larco 123, Miraflores")
                .phone("+5114445566")
                .location(GeoUtils.createPoint(-12.1191, -77.0282))
                .build();

        assignedDriver = User.builder().id(UUID.randomUUID()).fullName("Carlos Repartidor").phone("+51999888777").build();
        otherDriver = User.builder().id(UUID.randomUUID()).fullName("Otro Repartidor").phone("+51999111000").build();
    }

    @Test
    @DisplayName("DriverController RBAC: permite OWNER, ADMIN o REPARTIDOR; rechaza COCINERO y no miembros")
    void driverEndpointsEnforceRoleAuthorization() throws Exception {
        UUID orderId = UUID.randomUUID();
        DriverLocationUpdateRequest ping = DriverLocationUpdateRequest.builder()
                .latitude(-12.1150)
                .longitude(-77.0300)
                .build();

        when(tenantAccessService.hasAnyRole(
                tenantId,
                TenantStaffRole.OWNER,
                TenantStaffRole.ADMIN,
                TenantStaffRole.REPARTIDOR
        )).thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
                proxiedDriverController.getDriverOrders(tenantId, null)
        );
        assertThrows(AccessDeniedException.class, () ->
                proxiedDriverController.getDriverOrderById(tenantId, orderId)
        );
        assertThrows(AccessDeniedException.class, () ->
                proxiedDriverController.updateDriverOrderStatus(
                        tenantId, orderId, new DriverOrderStatusRequest(OrderStatus.EN_CAMINO)
                )
        );
        assertThrows(AccessDeniedException.class, () ->
                proxiedDriverController.updateDriverLocation(tenantId, orderId, ping)
        );
        assertThrows(AccessDeniedException.class, () ->
                proxiedDriverController.getDriverOrderTracking(tenantId, orderId, false)
        );

        when(tenantAccessService.hasAnyRole(
                tenantId,
                TenantStaffRole.OWNER,
                TenantStaffRole.ADMIN,
                TenantStaffRole.REPARTIDOR
        )).thenReturn(true);
        when(currentUserProvider.getCurrentUserId()).thenReturn(assignedDriver.getId());
        when(orderRepository.findAllByTenantIdAndDeliveryStaffIdAndStatusInOrderByCreatedAtAsc(
                tenantId,
                assignedDriver.getId(),
                EnumSet.of(
                        OrderStatus.EN_PREPARACION,
                        OrderStatus.LISTO_PARA_ENTREGA,
                        OrderStatus.EN_CAMINO,
                        OrderStatus.ENTREGADO
                )
        )).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/driver/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /driver/orders: devuelve órdenes asignadas con pickup del local, rechaza PENDIENTE/CANCELADO y omite campos financieros")
    void getDriverOrdersScopedAndStripsFinancialFields() throws Exception {
        when(tenantAccessService.hasAnyRole(
                tenantId,
                TenantStaffRole.OWNER,
                TenantStaffRole.ADMIN,
                TenantStaffRole.REPARTIDOR
        )).thenReturn(true);
        when(currentUserProvider.getCurrentUserId()).thenReturn(assignedDriver.getId());

        Order readyOrder = buildOrder("ORD-000100", OrderStatus.LISTO_PARA_ENTREGA, assignedDriver);

        when(orderRepository.findAllByTenantIdAndDeliveryStaffIdAndStatusInOrderByCreatedAtAsc(
                tenantId,
                assignedDriver.getId(),
                EnumSet.of(
                        OrderStatus.EN_PREPARACION,
                        OrderStatus.LISTO_PARA_ENTREGA,
                        OrderStatus.EN_CAMINO,
                        OrderStatus.ENTREGADO
                )
        )).thenReturn(List.of(readyOrder));

        MvcResult result = mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/driver/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].orderNumber").value("ORD-000100"))
                .andExpect(jsonPath("$.data[0].customerFullName").value("Lucía Mendoza"))
                .andExpect(jsonPath("$.data[0].customerPhone").value("+51987654321"))
                .andExpect(jsonPath("$.data[0].deliveryAddress").value("Av. Arequipa 4100"))
                .andExpect(jsonPath("$.data[0].pickupStoreName").value("Burger Bros"))
                .andExpect(jsonPath("$.data[0].pickupAddress").value("Av. Larco 123, Miraflores"))
                .andExpect(jsonPath("$.data[0].pickupPhone").value("+5114445566"))
                .andExpect(jsonPath("$.data[0].pickupLatitude").value(-12.1191))
                .andExpect(jsonPath("$.data[0].pickupLongitude").value(-77.0282))
                .andReturn();

        assertNoForbiddenJsonFields(result.getResponse().getContentAsString());

        when(orderRepository.findAllByTenantIdAndDeliveryStaffIdAndStatusOrderByCreatedAtAsc(
                tenantId, assignedDriver.getId(), OrderStatus.LISTO_PARA_ENTREGA
        )).thenReturn(List.of(readyOrder));

        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/driver/orders").param("status", "LISTO_PARA_ENTREGA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("LISTO_PARA_ENTREGA"));

        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/driver/orders").param("status", "PENDIENTE"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/driver/orders").param("status", "CANCELADO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET/PATCH/PUT /driver/orders/{id}*: aísla pedidos sin asignar o de otro repartidor con 403 y permite al repartidor asignado")
    void driverOrderDetailStatusAndTrackingEnforceDriverIsolation() throws Exception {
        when(tenantAccessService.hasAnyRole(
                tenantId,
                TenantStaffRole.OWNER,
                TenantStaffRole.ADMIN,
                TenantStaffRole.REPARTIDOR
        )).thenReturn(true);
        when(tenantAccessService.isSuperAdmin()).thenReturn(false);
        when(tenantAccessService.getCurrentUserRoleInTenant(tenantId))
                .thenReturn(Optional.of(TenantStaffRole.REPARTIDOR));
        when(currentUserProvider.getCurrentUserId()).thenReturn(assignedDriver.getId());

        Order unassignedOrder = buildOrder("ORD-000200", OrderStatus.LISTO_PARA_ENTREGA, null);
        Order otherDriverOrder = buildOrder("ORD-000201", OrderStatus.EN_CAMINO, otherDriver);
        Order ownOrder = buildOrder("ORD-000202", OrderStatus.LISTO_PARA_ENTREGA, assignedDriver);

        when(orderRepository.findByIdAndTenantId(unassignedOrder.getId(), tenantId)).thenReturn(Optional.of(unassignedOrder));
        when(orderRepository.findByIdAndTenantId(otherDriverOrder.getId(), tenantId)).thenReturn(Optional.of(otherDriverOrder));
        when(orderRepository.findByIdAndTenantId(ownOrder.getId(), tenantId)).thenReturn(Optional.of(ownOrder));
        when(orderRepository.findByIdAndTenantIdForUpdate(unassignedOrder.getId(), tenantId)).thenReturn(Optional.of(unassignedOrder));
        when(orderRepository.findByIdAndTenantIdForUpdate(otherDriverOrder.getId(), tenantId)).thenReturn(Optional.of(otherDriverOrder));
        when(orderRepository.findByIdAndTenantIdForUpdate(ownOrder.getId(), tenantId)).thenReturn(Optional.of(ownOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Aislamiento: sin asignar -> 403 en GET detalle, PATCH estado y GET tracking
        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/driver/orders/" + unassignedOrder.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/tenants/" + tenantId + "/driver/orders/" + unassignedOrder.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DriverOrderStatusRequest(OrderStatus.EN_CAMINO))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/driver/orders/" + unassignedOrder.getId() + "/tracking"))
                .andExpect(status().isForbidden());

        // 2. Aislamiento: asignado a otro repartidor -> 403 en GET detalle, PATCH estado, PUT location y GET tracking
        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/driver/orders/" + otherDriverOrder.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/tenants/" + tenantId + "/driver/orders/" + otherDriverOrder.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DriverOrderStatusRequest(OrderStatus.ENTREGADO))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/tenants/" + tenantId + "/driver/orders/" + otherDriverOrder.getId() + "/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                DriverLocationUpdateRequest.builder().latitude(-12.1120).longitude(-77.0300).build()
                        )))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/driver/orders/" + otherDriverOrder.getId() + "/tracking"))
                .andExpect(status().isForbidden());

        // 3. Repartidor asignado -> GET detalle, PATCH EN_CAMINO, PUT location y GET tracking funcionan y sin campos financieros
        MvcResult detailRes = mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/driver/orders/" + ownOrder.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNumber").value("ORD-000202"))
                .andReturn();
        assertNoForbiddenJsonFields(detailRes.getResponse().getContentAsString());

        when(trackingRepository.findByOrderIdAndTenantId(ownOrder.getId(), tenantId)).thenReturn(Optional.empty());
        when(trackingRepository.save(any(DeliveryTracking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any(DeliveryLocationHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        MvcResult patchRes = mockMvc.perform(patch("/api/v1/tenants/" + tenantId + "/driver/orders/" + ownOrder.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DriverOrderStatusRequest(OrderStatus.EN_CAMINO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EN_CAMINO"))
                .andReturn();
        assertNoForbiddenJsonFields(patchRes.getResponse().getContentAsString());

        // PATCH con estado no permitido para repartidor -> 400 BadRequest
        mockMvc.perform(patch("/api/v1/tenants/" + tenantId + "/driver/orders/" + ownOrder.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DriverOrderStatusRequest(OrderStatus.CANCELADO))))
                .andExpect(status().isBadRequest());

        MvcResult locRes = mockMvc.perform(put("/api/v1/tenants/" + tenantId + "/driver/orders/" + ownOrder.getId() + "/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                DriverLocationUpdateRequest.builder().latitude(-12.1120).longitude(-77.0300).speedKmh(25.0).build()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.driverLatitude").value(-12.1120))
                .andExpect(jsonPath("$.data.trackingActive").value(true))
                .andReturn();
        assertNoForbiddenJsonFields(locRes.getResponse().getContentAsString());

        MvcResult trackingRes = mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/driver/orders/" + ownOrder.getId() + "/tracking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNumber").value("ORD-000202"))
                .andReturn();
        assertNoForbiddenJsonFields(trackingRes.getResponse().getContentAsString());
    }

    private Order buildOrder(String orderNumber, OrderStatus status, User driver) {
        UUID customerId = UUID.randomUUID();
        User customerUser = User.builder()
                .id(customerId)
                .fullName("Lucía Mendoza")
                .email("lucia@nexofood.lat")
                .phone("+51987654321")
                .build();
        TenantCustomer customer = TenantCustomer.builder()
                .id(new TenantCustomerId(tenantId, customerId))
                .tenant(tenant)
                .user(customerUser)
                .build();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .customer(customer)
                .customerId(customerId)
                .deliveryStaff(driver)
                .orderNumber(orderNumber)
                .ruc("20123456789")
                .deliveryType(DeliveryType.DELIVERY)
                .status(status)
                .deliveryAddress("Av. Arequipa 4100")
                .deliveryLocation(GeoUtils.createPoint(-12.1040, -77.0315))
                .estimatedDistanceKm(new BigDecimal("2.10"))
                .estimatedDeliveryMinutes(12)
                .estimatedArrivalAt(OffsetDateTime.now().plusMinutes(12))
                .subtotal(new BigDecimal("50.00"))
                .taxTotal(new BigDecimal("9.00"))
                .deliveryFee(new BigDecimal("6.50"))
                .total(new BigDecimal("65.50"))
                .notes("Tocar timbre 202")
                .build();
        OffsetDateTime now = OffsetDateTime.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .tenant(tenant)
                .productName("Combo Burger")
                .unitPrice(new BigDecimal("50.00"))
                .quantity(1)
                .subtotal(new BigDecimal("50.00"))
                .taxAmount(new BigDecimal("9.00"))
                .notes("Papas crocantes")
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
                assertFalse(FORBIDDEN_FIELDS.contains(fieldName), "Campo prohibido expuesto en JSON de repartidor: " + fieldName);
                assertTreeHasNoForbiddenFields(node.get(fieldName));
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                assertTreeHasNoForbiddenFields(child);
            }
        }
    }
}
