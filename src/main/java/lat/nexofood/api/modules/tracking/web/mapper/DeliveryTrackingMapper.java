package lat.nexofood.api.modules.tracking.web.mapper;

import lat.nexofood.api.common.util.GeoUtils;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.tracking.application.config.TrackingProperties;
import lat.nexofood.api.modules.tracking.domain.DeliveryLocationHistory;
import lat.nexofood.api.modules.tracking.domain.DeliveryTracking;
import lat.nexofood.api.modules.tracking.web.dto.response.DeliveryTrackingResponse;
import lat.nexofood.api.modules.tracking.web.dto.response.TrackingPointResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

@Component
public class DeliveryTrackingMapper {

    private final TrackingProperties properties;

    public DeliveryTrackingMapper() {
        this(new TrackingProperties());
    }

    @Autowired
    public DeliveryTrackingMapper(TrackingProperties properties) {
        this.properties = properties != null ? properties : new TrackingProperties();
    }

    public TrackingPointResponse toPointResponse(DeliveryLocationHistory history) {
        if (history == null) {
            return null;
        }
        return TrackingPointResponse.builder()
                .latitude(GeoUtils.getLatitude(history.getLocation()))
                .longitude(GeoUtils.getLongitude(history.getLocation()))
                .heading(history.getHeading())
                .speedKmh(history.getSpeedKmh())
                .accuracyM(history.getAccuracyM())
                .recordedAt(history.getRecordedAt())
                .build();
    }

    public DeliveryTrackingResponse toResponse(
            Order order,
            DeliveryTracking tracking,
            List<DeliveryLocationHistory> history,
            boolean includeLiveDriverCoords,
            boolean throttled
    ) {
        if (order == null) {
            return null;
        }

        Tenant tenant = order.getTenant();
        User driver = tracking != null && tracking.getDriver() != null
                ? tracking.getDriver()
                : order.getDeliveryStaff();

        boolean orderInTransit = order.getStatus() == OrderStatus.EN_CAMINO;
        boolean trackingActive = orderInTransit
                && tracking != null
                && Boolean.TRUE.equals(tracking.getIsActive());

        boolean exposeDriverPosition = includeLiveDriverCoords && tracking != null && tracking.getLocation() != null;

        Integer etaMinutes = tracking != null && tracking.getEtaMinutes() != null
                ? tracking.getEtaMinutes()
                : order.getEstimatedDeliveryMinutes();

        BigDecimal remainingKm = tracking != null && tracking.getRemainingDistanceKm() != null
                ? tracking.getRemainingDistanceKm()
                : order.getEstimatedDistanceKm();

        Integer etaMin = etaMinutes != null ? Math.max(1, (int) Math.floor(etaMinutes * 0.85)) : null;
        Integer etaMax = etaMinutes != null ? Math.max(etaMinutes + 2, (int) Math.ceil(etaMinutes * 1.25)) : null;

        OffsetDateTime lastUpdatedAt = tracking != null ? tracking.getRecordedAt() : null;
        boolean stale = false;
        if (orderInTransit) {
            if (lastUpdatedAt == null) {
                stale = true;
            } else {
                long secondsSinceUpdate = Duration.between(lastUpdatedAt, OffsetDateTime.now()).getSeconds();
                stale = secondsSinceUpdate > properties.getStaleAfterSeconds();
            }
        }

        List<TrackingPointResponse> routePoints = history != null
                ? history.stream().map(this::toPointResponse).toList()
                : Collections.emptyList();

        return DeliveryTrackingResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getStatus())
                .tenantId(tenant != null ? tenant.getId() : null)
                .tenantName(tenant != null ? tenant.getName() : null)
                .storeLatitude(tenant != null ? GeoUtils.getLatitude(tenant.getLocation()) : null)
                .storeLongitude(tenant != null ? GeoUtils.getLongitude(tenant.getLocation()) : null)
                .deliveryAddress(order.getDeliveryAddress())
                .destinationLatitude(GeoUtils.getLatitude(order.getDeliveryLocation()))
                .destinationLongitude(GeoUtils.getLongitude(order.getDeliveryLocation()))
                .driverId(driver != null ? driver.getId() : null)
                .driverName(driver != null ? driver.getFullName() : null)
                .driverPhone(driver != null ? driver.getPhone() : null)
                .driverLatitude(exposeDriverPosition ? GeoUtils.getLatitude(tracking.getLocation()) : null)
                .driverLongitude(exposeDriverPosition ? GeoUtils.getLongitude(tracking.getLocation()) : null)
                .heading(exposeDriverPosition ? tracking.getHeading() : null)
                .speedKmh(exposeDriverPosition ? tracking.getSpeedKmh() : null)
                .accuracyM(exposeDriverPosition ? tracking.getAccuracyM() : null)
                .remainingDistanceKm(remainingKm)
                .etaMinutes(etaMinutes)
                .etaMinMinutes(etaMin)
                .etaMaxMinutes(etaMax)
                .estimatedArrivalAt(order.getEstimatedArrivalAt())
                .etaMessage(resolveEtaMessage(order, etaMinutes))
                .trackingActive(trackingActive)
                .stale(stale)
                .throttled(throttled)
                .pollIntervalSeconds(properties.getPollIntervalSeconds())
                .lastUpdatedAt(lastUpdatedAt)
                .dispatchedAt(order.getDispatchedAt())
                .deliveredAt(order.getDeliveredAt())
                .routeHistory(routePoints)
                .build();
    }

    private String resolveEtaMessage(Order order, Integer etaMinutes) {
        if (order.getStatus() == OrderStatus.ENTREGADO) {
            return "Tu pedido ha sido entregado";
        }
        if (order.getStatus() == OrderStatus.CANCELADO) {
            return "Tu pedido fue cancelado";
        }
        if (etaMinutes != null && etaMinutes > 0) {
            return "Tu pedido estará en tu casa en ~" + etaMinutes + " min";
        }
        return switch (order.getStatus()) {
            case PENDIENTE -> "Tu pedido está pendiente de confirmación";
            case EN_PREPARACION -> "Tu pedido se está preparando en cocina";
            case LISTO_PARA_ENTREGA -> "Tu pedido está listo y esperando al repartidor";
            case EN_CAMINO -> "El repartidor está en camino a tu domicilio";
            default -> null;
        };
    }
}

