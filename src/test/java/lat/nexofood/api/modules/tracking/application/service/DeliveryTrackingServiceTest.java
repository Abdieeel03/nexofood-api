package lat.nexofood.api.modules.tracking.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ForbiddenException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.common.util.GeoUtils;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.infrastructure.repository.OrderRepository;
import lat.nexofood.api.modules.store.application.service.customer.CurrentCustomerResolver;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lat.nexofood.api.modules.store.domain.TenantCustomerId;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lat.nexofood.api.modules.tracking.application.config.TrackingProperties;
import lat.nexofood.api.modules.tracking.domain.DeliveryLocationHistory;
import lat.nexofood.api.modules.tracking.domain.DeliveryTracking;
import lat.nexofood.api.modules.tracking.infrastructure.repository.DeliveryLocationHistoryRepository;
import lat.nexofood.api.modules.tracking.infrastructure.repository.DeliveryTrackingRepository;
import lat.nexofood.api.modules.tracking.web.dto.request.DriverLocationUpdateRequest;
import lat.nexofood.api.modules.tracking.web.dto.response.DeliveryEstimateResponse;
import lat.nexofood.api.modules.tracking.web.dto.response.DeliveryTrackingResponse;
import lat.nexofood.api.modules.tracking.web.mapper.DeliveryTrackingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryTrackingServiceTest {

    @Mock
    private DeliveryTrackingRepository trackingRepository;
    @Mock
    private DeliveryLocationHistoryRepository historyRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private TenantAccessService tenantAccessService;
    @Mock
    private CurrentCustomerResolver currentCustomerResolver;

    private TrackingProperties properties;
    private DeliveryTrackingServiceImpl trackingService;

    private Tenant tenant;
    private User driverUser;
    private User customerUser;
    private TenantCustomer tenantCustomer;
    private Order order;

    @BeforeEach
    void setUp() {
        properties = new TrackingProperties();
        properties.setMinPingIntervalSeconds(5);
        properties.setPollIntervalSeconds(15);
        properties.setStaleAfterSeconds(60);
        properties.setMaxPlausibleSpeedKmh(150.0);

        EtaProvider etaProvider = new HaversineEtaProvider(properties, null);
        DeliveryTrackingMapper mapper = new DeliveryTrackingMapper(properties);

        trackingService = new DeliveryTrackingServiceImpl(
                trackingRepository,
                historyRepository,
                orderRepository,
                tenantRepository,
                etaProvider,
                properties,
                mapper,
                currentUserProvider,
                tenantAccessService,
                currentCustomerResolver
        );

        tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Burger Bros")
                .slug("burger-bros")
                .location(GeoUtils.createPoint(-12.1191, -77.0282)) // Miraflores
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .defaultDeliveryFee(new BigDecimal("6.50"))
                .isActive(true)
                .build();

        driverUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("Carlos Motorizado")
                .phone("+51999888777")
                .build();

        customerUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("Lucía Cliente")
                .build();

        tenantCustomer = TenantCustomer.builder()
                .id(new TenantCustomerId(tenant.getId(), customerUser.getId()))
                .tenant(tenant)
                .user(customerUser)
                .build();

        order = Order.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .customer(tenantCustomer)
                .deliveryStaff(driverUser)
                .orderNumber("ORD-000010")
                .deliveryType(DeliveryType.DELIVERY)
                .status(OrderStatus.EN_CAMINO)
                .deliveryAddress("Av. Arequipa 4100, Miraflores")
                .deliveryLocation(GeoUtils.createPoint(-12.1040, -77.0315)) // ~1.7 km al norte
                .build();
    }

    @Test
    @DisplayName("updateDriverLocation: guarda ping del repartidor asignado, recalcula ETA y registra historial")
    void updateDriverLocationSavesPingAndRecalculatesEta() {
        when(orderRepository.findByIdAndTenantId(order.getId(), tenant.getId())).thenReturn(Optional.of(order));
        when(currentUserProvider.getCurrentUserId()).thenReturn(driverUser.getId());
        when(tenantAccessService.isSuperAdmin()).thenReturn(false);
        when(tenantAccessService.getCurrentUserRoleInTenant(tenant.getId()))
                .thenReturn(Optional.of(TenantStaffRole.REPARTIDOR));
        when(trackingRepository.findByOrderIdAndTenantId(order.getId(), tenant.getId()))
                .thenReturn(Optional.empty());
        when(trackingRepository.save(any(DeliveryTracking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(historyRepository.save(any(DeliveryLocationHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        DriverLocationUpdateRequest request = DriverLocationUpdateRequest.builder()
                .latitude(-12.1120)
                .longitude(-77.0300)
                .heading(350.0)
                .speedKmh(28.0)
                .accuracyM(8.5)
                .build();

        DeliveryTrackingResponse response = trackingService.updateDriverLocation(
                tenant.getId(), order.getId(), request
        );

        assertEquals(order.getId(), response.orderId());
        assertEquals(-12.1120, response.driverLatitude());
        assertEquals(-77.0300, response.driverLongitude());
        assertTrue(response.trackingActive());
        assertFalse(response.throttled());
        assertNotNull(response.etaMinutes());
        assertTrue(response.etaMinutes() > 0);
        assertEquals("Tu pedido estará en tu casa en ~" + response.etaMinutes() + " min", response.etaMessage());

        verify(trackingRepository).save(any(DeliveryTracking.class));
        verify(historyRepository).save(any(DeliveryLocationHistory.class));
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("updateDriverLocation: aplica throttling cuando el repartidor envía dos pings en menos de 5 segundos")
    void updateDriverLocationThrottlesRapidPings() {
        DeliveryTracking recentTracking = DeliveryTracking.builder()
                .orderId(order.getId())
                .order(order)
                .tenant(tenant)
                .driver(driverUser)
                .location(GeoUtils.createPoint(-12.1120, -77.0300))
                .etaMinutes(7)
                .remainingDistanceKm(new BigDecimal("1.20"))
                .isActive(true)
                .recordedAt(OffsetDateTime.now().minusSeconds(2))
                .updatedAt(OffsetDateTime.now().minusSeconds(2))
                .build();

        when(orderRepository.findByIdAndTenantId(order.getId(), tenant.getId())).thenReturn(Optional.of(order));
        when(currentUserProvider.getCurrentUserId()).thenReturn(driverUser.getId());
        when(tenantAccessService.isSuperAdmin()).thenReturn(false);
        when(tenantAccessService.getCurrentUserRoleInTenant(tenant.getId()))
                .thenReturn(Optional.of(TenantStaffRole.REPARTIDOR));
        when(trackingRepository.findByOrderIdAndTenantId(order.getId(), tenant.getId()))
                .thenReturn(Optional.of(recentTracking));

        DriverLocationUpdateRequest request = DriverLocationUpdateRequest.builder()
                .latitude(-12.1115)
                .longitude(-77.0301)
                .build();

        DeliveryTrackingResponse response = trackingService.updateDriverLocation(
                tenant.getId(), order.getId(), request
        );

        assertTrue(response.throttled());
        verify(trackingRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateDriverLocation: rechaza actualización si otro repartidor intenta enviar coordenadas o si no está EN_CAMINO")
    void updateDriverLocationRejectsOtherDriverOrInvalidStatus() {
        when(orderRepository.findByIdAndTenantId(order.getId(), tenant.getId())).thenReturn(Optional.of(order));
        when(currentUserProvider.getCurrentUserId()).thenReturn(UUID.randomUUID()); // otro repartidor
        when(tenantAccessService.isSuperAdmin()).thenReturn(false);
        when(tenantAccessService.getCurrentUserRoleInTenant(tenant.getId()))
                .thenReturn(Optional.of(TenantStaffRole.REPARTIDOR));

        DriverLocationUpdateRequest request = DriverLocationUpdateRequest.builder()
                .latitude(-12.1120)
                .longitude(-77.0300)
                .build();

        assertThrows(ForbiddenException.class, () ->
                trackingService.updateDriverLocation(tenant.getId(), order.getId(), request)
        );

        // Si el pedido aún está en LISTO_PARA_ENTREGA tampoco permite pings en tiempo real
        order.setStatus(OrderStatus.LISTO_PARA_ENTREGA);
        assertThrows(BadRequestException.class, () ->
                trackingService.updateDriverLocation(tenant.getId(), order.getId(), request)
        );
    }

    @Test
    @DisplayName("getOrderTrackingForCustomer: oculta coordenadas del repartidor cuando el pedido no está EN_CAMINO")
    void getOrderTrackingForCustomerHidesDriverLocationWhenNotInTransit() {
        DeliveryTracking tracking = DeliveryTracking.builder()
                .orderId(order.getId())
                .order(order)
                .tenant(tenant)
                .driver(driverUser)
                .location(GeoUtils.createPoint(-12.1120, -77.0300))
                .etaMinutes(8)
                .isActive(true)
                .recordedAt(OffsetDateTime.now())
                .build();

        when(currentCustomerResolver.resolveBySlug("burger-bros")).thenReturn(tenantCustomer);
        when(orderRepository.findByIdAndTenantIdAndCustomerUserId(order.getId(), tenant.getId(), customerUser.getId()))
                .thenReturn(Optional.of(order));
        when(trackingRepository.findByOrderIdAndTenantId(order.getId(), tenant.getId()))
                .thenReturn(Optional.of(tracking));

        // 1. Mientras está EN_CAMINO el cliente ve las coordenadas del repartidor
        DeliveryTrackingResponse inTransit = trackingService.getOrderTrackingForCustomer("burger-bros", order.getId());
        assertEquals(-12.1120, inTransit.driverLatitude());
        assertEquals(-77.0300, inTransit.driverLongitude());
        assertTrue(inTransit.trackingActive());

        // 2. Cuando pasa a ENTREGADO, ya no expone la ubicación en vivo del repartidor
        order.setStatus(OrderStatus.ENTREGADO);
        tracking.setIsActive(false);
        DeliveryTrackingResponse delivered = trackingService.getOrderTrackingForCustomer("burger-bros", order.getId());
        assertNull(delivered.driverLatitude());
        assertNull(delivered.driverLongitude());
        assertFalse(delivered.trackingActive());
        assertEquals("Tu pedido ha sido entregado", delivered.etaMessage());
    }

    @Test
    @DisplayName("estimateDeliveryBySlug: informa cobertura, tarifa de envío y ETA antes del checkout")
    void estimateDeliveryBySlugCalculatesCoverageAndEta() {
        when(tenantRepository.findBySlug("burger-bros")).thenReturn(Optional.of(tenant));

        // Dentro de cobertura (~1.7 km < 5.0 km)
        DeliveryEstimateResponse inside = trackingService.estimateDeliveryBySlug(
                "burger-bros", -12.1040, -77.0315
        );
        assertTrue(inside.withinCoverage());
        assertEquals(new BigDecimal("6.50"), inside.deliveryFee());
        assertNotNull(inside.estimatedDeliveryMinutes());
        assertTrue(inside.message().contains("Tu pedido estará en tu casa en ~"));

        // Fuera de cobertura (> 10 km)
        DeliveryEstimateResponse outside = trackingService.estimateDeliveryBySlug(
                "burger-bros", -12.0150, -77.0850
        );
        assertFalse(outside.withinCoverage());
        assertNull(outside.estimatedDeliveryMinutes());
        assertTrue(outside.message().contains("fuera de la zona de cobertura"));
    }
}

