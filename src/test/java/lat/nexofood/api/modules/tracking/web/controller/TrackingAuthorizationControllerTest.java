package lat.nexofood.api.modules.tracking.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lat.nexofood.api.common.exception.GlobalExceptionHandler;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.tracking.application.service.DeliveryTrackingService;
import lat.nexofood.api.modules.tracking.web.dto.request.DriverLocationUpdateRequest;
import lat.nexofood.api.modules.tracking.web.dto.response.DeliveryEstimateResponse;
import lat.nexofood.api.modules.tracking.web.dto.response.DeliveryTrackingResponse;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
class TrackingAuthorizationControllerTest {

    private DeliveryTrackingService trackingService;
    private TenantAccessService tenantAccessService;

    private DeliveryTrackingController proxiedTrackingController;
    private StorefrontTrackingController storefrontTrackingController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        trackingService = mock(DeliveryTrackingService.class);
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

        proxiedTrackingController = createSecuredProxy(
                new DeliveryTrackingController(trackingService), interceptor
        );
        storefrontTrackingController = new StorefrontTrackingController(trackingService);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        proxiedTrackingController,
                        storefrontTrackingController
                )
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
    @DisplayName("DeliveryTrackingController: protege actualización GPS, consulta de orden y mapa activo según rol del staff")
    void trackingControllerEnforcesRoleAuthorization() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        DriverLocationUpdateRequest ping = DriverLocationUpdateRequest.builder()
                .latitude(-12.1120)
                .longitude(-77.0300)
                .speedKmh(25.0)
                .build();

        // 1. COCINERO no puede enviar pings GPS (solo OWNER, ADMIN o REPARTIDOR)
        when(tenantAccessService.hasAnyRole(
                tenantId,
                TenantStaffRole.OWNER,
                TenantStaffRole.ADMIN,
                TenantStaffRole.REPARTIDOR
        )).thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
                proxiedTrackingController.updateDriverLocation(tenantId, orderId, ping)
        );

        // 2. REPARTIDOR autorizado sí puede enviar ping GPS
        when(tenantAccessService.hasAnyRole(
                tenantId,
                TenantStaffRole.OWNER,
                TenantStaffRole.ADMIN,
                TenantStaffRole.REPARTIDOR
        )).thenReturn(true);

        DeliveryTrackingResponse trackingResp = DeliveryTrackingResponse.builder()
                .orderId(orderId)
                .orderNumber("ORD-000015")
                .orderStatus(OrderStatus.EN_CAMINO)
                .driverLatitude(-12.1120)
                .driverLongitude(-77.0300)
                .etaMinutes(11)
                .etaMessage("Tu pedido estará en tu casa en ~11 min")
                .trackingActive(true)
                .pollIntervalSeconds(15)
                .build();

        when(trackingService.updateDriverLocation(eq(tenantId), eq(orderId), any())).thenReturn(trackingResp);

        mockMvc.perform(put("/api/v1/tenants/" + tenantId + "/orders/" + orderId + "/tracking/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ping)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.etaMinutes").value(11))
                .andExpect(jsonPath("$.data.etaMessage").value("Tu pedido estará en tu casa en ~11 min"));

        // 3. Mapa de repartidores activos solo accesible para OWNER o ADMIN
        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(false);
        assertThrows(AccessDeniedException.class, () ->
                proxiedTrackingController.getActiveDeliveries(tenantId)
        );

        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(true);
        when(trackingService.getActiveTrackingsByTenant(tenantId)).thenReturn(List.of(trackingResp));

        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/tracking/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderNumber").value("ORD-000015"));
    }

    @Test
    @DisplayName("StorefrontTrackingController: permite al cliente consultar el tracking de su pedido y estimar cobertura/ETA")
    void storefrontTrackingEndpointsWorkCorrectly() throws Exception {
        UUID orderId = UUID.randomUUID();
        DeliveryTrackingResponse trackingResp = DeliveryTrackingResponse.builder()
                .orderId(orderId)
                .orderNumber("ORD-000020")
                .orderStatus(OrderStatus.EN_CAMINO)
                .driverLatitude(-12.1150)
                .driverLongitude(-77.0290)
                .etaMinutes(9)
                .etaMessage("Tu pedido estará en tu casa en ~9 min")
                .trackingActive(true)
                .pollIntervalSeconds(15)
                .build();

        DeliveryEstimateResponse estimateResp = DeliveryEstimateResponse.builder()
                .tenantSlug("burger-bros")
                .tenantName("Burger Bros")
                .withinCoverage(true)
                .straightDistanceKm(new BigDecimal("1.80"))
                .estimatedRouteDistanceKm(new BigDecimal("2.43"))
                .deliveryFee(new BigDecimal("6.50"))
                .estimatedDeliveryMinutes(11)
                .message("Tu pedido estará en tu casa en ~11 min")
                .build();

        when(trackingService.getOrderTrackingForCustomer("burger-bros", orderId)).thenReturn(trackingResp);
        when(trackingService.estimateDeliveryBySlug("burger-bros", -12.1150, -77.0290)).thenReturn(estimateResp);

        mockMvc.perform(get("/api/v1/store/burger-bros/orders/" + orderId + "/tracking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.etaMinutes").value(9))
                .andExpect(jsonPath("$.data.pollIntervalSeconds").value(15));

        mockMvc.perform(get("/api/v1/store/burger-bros/delivery-estimate")
                        .param("lat", "-12.1150")
                        .param("lng", "-77.0290"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withinCoverage").value(true))
                .andExpect(jsonPath("$.data.estimatedDeliveryMinutes").value(11));
    }
}

