package lat.nexofood.api.modules.tracking.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.util.GeoUtils;
import lat.nexofood.api.modules.tracking.application.config.TrackingProperties;
import lat.nexofood.api.modules.tracking.infrastructure.repository.DeliveryTrackingRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

@Component
public class HaversineEtaProvider implements EtaProvider {

    private final TrackingProperties properties;
    private final DeliveryTrackingRepository trackingRepository;

    public HaversineEtaProvider() {
        this(new TrackingProperties(), null);
    }

    public HaversineEtaProvider(TrackingProperties properties) {
        this(properties, null);
    }

    @Autowired
    public HaversineEtaProvider(
            TrackingProperties properties,
            @Autowired(required = false) DeliveryTrackingRepository trackingRepository
    ) {
        this.properties = properties != null ? properties : new TrackingProperties();
        this.trackingRepository = trackingRepository;
    }

    @Override
    public EtaEstimate estimateFromStoreToCustomer(Point storeLocation, Point customerLocation) {
        int totalBuffer = Math.max(0, properties.getPickupBufferMinutes())
                + Math.max(0, properties.getDropoffBufferMinutes());
        return computeEstimate(storeLocation, customerLocation, null, totalBuffer);
    }

    @Override
    public EtaEstimate estimateRemainingFromDriver(
            Point driverLocation,
            Point customerLocation,
            Double currentSpeedKmh
    ) {
        int dropoffBuffer = Math.max(0, properties.getDropoffBufferMinutes());
        return computeEstimate(driverLocation, customerLocation, currentSpeedKmh, dropoffBuffer);
    }

    private EtaEstimate computeEstimate(
            Point from,
            Point to,
            Double currentSpeedKmh,
            int bufferMinutes
    ) {
        if (from == null || to == null) {
            throw new BadRequestException("Las coordenadas de origen y destino son obligatorias para calcular el tiempo estimado");
        }

        double straightKm = resolveDistanceKm(from, to);
        double roadFactor = properties.getRoadFactor() > 0 ? properties.getRoadFactor() : 1.35;
        double routeKm = straightKm * roadFactor;

        double defaultSpeed = properties.getAverageSpeedKmh() > 0 ? properties.getAverageSpeedKmh() : 25.0;
        double effectiveSpeed = defaultSpeed;
        if (currentSpeedKmh != null && currentSpeedKmh >= 8.0 && currentSpeedKmh <= 80.0) {
            // Promedio ponderado entre la velocidad histórica urbana y la velocidad actual del repartidor
            effectiveSpeed = (defaultSpeed * 0.6) + (currentSpeedKmh * 0.4);
        }

        double travelMinutes = (routeKm / effectiveSpeed) * 60.0;
        int rawMinutes = (int) Math.ceil(travelMinutes) + bufferMinutes;
        int minAllowed = Math.max(1, properties.getMinEtaMinutes());
        int estimatedMinutes = Math.max(minAllowed, rawMinutes);

        int minRange = Math.max(1, (int) Math.floor(estimatedMinutes * 0.85));
        int maxRange = Math.max(estimatedMinutes + 2, (int) Math.ceil(estimatedMinutes * 1.25));

        OffsetDateTime arrivalAt = OffsetDateTime.now().plusMinutes(estimatedMinutes);
        String message = "Tu pedido estará en tu casa en ~" + estimatedMinutes + " min";

        return EtaEstimate.builder()
                .straightDistanceKm(BigDecimal.valueOf(straightKm).setScale(2, RoundingMode.HALF_UP))
                .estimatedRouteDistanceKm(BigDecimal.valueOf(routeKm).setScale(2, RoundingMode.HALF_UP))
                .estimatedMinutes(estimatedMinutes)
                .minMinutes(minRange)
                .maxMinutes(maxRange)
                .estimatedArrivalAt(arrivalAt)
                .message(message)
                .build();
    }

    private double resolveDistanceKm(Point from, Point to) {
        if (trackingRepository != null) {
            try {
                Double postgisKm = trackingRepository.calculateDistanceSphereKm(
                        from.getY(), from.getX(), to.getY(), to.getX()
                );
                if (postgisKm != null && !Double.isNaN(postgisKm) && postgisKm >= 0.0) {
                    return postgisKm;
                }
            } catch (Exception ignored) {
                // Fallback transparente a cálculo esférico Haversine en memoria
            }
        }
        return GeoUtils.calculateDistanceKm(from, to);
    }
}

