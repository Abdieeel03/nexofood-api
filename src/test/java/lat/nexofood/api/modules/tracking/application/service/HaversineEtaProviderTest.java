package lat.nexofood.api.modules.tracking.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.util.GeoUtils;
import lat.nexofood.api.modules.tracking.application.config.TrackingProperties;
import lat.nexofood.api.modules.tracking.infrastructure.repository.DeliveryTrackingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HaversineEtaProviderTest {

    private TrackingProperties properties;
    private HaversineEtaProvider etaProvider;

    @BeforeEach
    void setUp() {
        properties = new TrackingProperties();
        properties.setAverageSpeedKmh(25.0);
        properties.setRoadFactor(1.35);
        properties.setPickupBufferMinutes(3);
        properties.setDropoffBufferMinutes(2);
        properties.setMinEtaMinutes(2);
        etaProvider = new HaversineEtaProvider(properties);
    }

    @Test
    @DisplayName("estimateFromStoreToCustomer: calcula distancia vial (×1.35), tiempo estimado con buffer (3+2 min) y mensaje amigable")
    void estimateFromStoreToCustomerCalculatesDistanceAndMinutes() {
        // Miraflores (Parque Kennedy) -> San Isidro (~3.10 km en línea recta)
        Point store = GeoUtils.createPoint(-12.1219, -77.0297);
        Point customer = GeoUtils.createPoint(-12.0941, -77.0325);

        EtaEstimate estimate = etaProvider.estimateFromStoreToCustomer(store, customer);

        assertNotNull(estimate);
        assertTrue(estimate.straightDistanceKm().compareTo(new BigDecimal("2.80")) > 0);
        assertTrue(estimate.estimatedRouteDistanceKm().compareTo(estimate.straightDistanceKm()) > 0);
        // ~3.10 km * 1.35 = ~4.18 km a 25 km/h ≈ 10.04 min -> ceil 11 + 5 buffer = 16 min
        assertTrue(estimate.estimatedMinutes() >= 14 && estimate.estimatedMinutes() <= 18);
        assertEquals("Tu pedido estará en tu casa en ~" + estimate.estimatedMinutes() + " min", estimate.message());
        assertNotNull(estimate.estimatedArrivalAt());
        assertTrue(estimate.minMinutes() <= estimate.estimatedMinutes());
        assertTrue(estimate.maxMinutes() > estimate.estimatedMinutes());
    }

    @Test
    @DisplayName("estimateRemainingFromDriver: usa ST_DistanceSphere de PostGIS cuando está disponible y pondera la velocidad actual")
    void estimateRemainingFromDriverUsesPostgisDistanceWhenAvailable() {
        DeliveryTrackingRepository repo = mock(DeliveryTrackingRepository.class);
        when(repo.calculateDistanceSphereKm(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(2.0); // 2.0 km exactos desde PostGIS

        HaversineEtaProvider providerWithPostgis = new HaversineEtaProvider(properties, repo);

        Point driver = GeoUtils.createPoint(-12.1100, -77.0300);
        Point customer = GeoUtils.createPoint(-12.0941, -77.0325);

        // 2.0 km * 1.35 = 2.70 km de ruta; sin velocidad reportada -> 25 km/h -> 6.48 min -> ceil 7 + 2 dropoff = 9 min
        EtaEstimate estimate = providerWithPostgis.estimateRemainingFromDriver(driver, customer, null);
        assertEquals(new BigDecimal("2.00"), estimate.straightDistanceKm());
        assertEquals(new BigDecimal("2.70"), estimate.estimatedRouteDistanceKm());
        assertEquals(9, estimate.estimatedMinutes());
        assertEquals("Tu pedido estará en tu casa en ~9 min", estimate.message());
    }

    @Test
    @DisplayName("estimateFromStoreToCustomer: lanza BadRequestException si falta alguna coordenada")
    void throwsWhenCoordinatesAreNull() {
        Point store = GeoUtils.createPoint(-12.1219, -77.0297);
        assertThrows(BadRequestException.class, () -> etaProvider.estimateFromStoreToCustomer(store, null));
        assertThrows(BadRequestException.class, () -> etaProvider.estimateFromStoreToCustomer(null, store));
    }
}

