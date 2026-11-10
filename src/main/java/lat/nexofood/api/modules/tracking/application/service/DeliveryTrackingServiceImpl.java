package lat.nexofood.api.modules.tracking.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ForbiddenException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
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
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class DeliveryTrackingServiceImpl implements DeliveryTrackingService {

    private final DeliveryTrackingRepository trackingRepository;
    private final DeliveryLocationHistoryRepository historyRepository;
    private final OrderRepository orderRepository;
    private final TenantRepository tenantRepository;
    private final EtaProvider etaProvider;
    private final TrackingProperties properties;
    private final DeliveryTrackingMapper trackingMapper;
    private final CurrentUserProvider currentUserProvider;
    private final TenantAccessService tenantAccessService;
    private final CurrentCustomerResolver currentCustomerResolver;

    @Autowired
    public DeliveryTrackingServiceImpl(
            DeliveryTrackingRepository trackingRepository,
            DeliveryLocationHistoryRepository historyRepository,
            OrderRepository orderRepository,
            TenantRepository tenantRepository,
            EtaProvider etaProvider,
            TrackingProperties properties,
            DeliveryTrackingMapper trackingMapper,
            CurrentUserProvider currentUserProvider,
            TenantAccessService tenantAccessService,
            CurrentCustomerResolver currentCustomerResolver
    ) {
        this.trackingRepository = trackingRepository;
        this.historyRepository = historyRepository;
        this.orderRepository = orderRepository;
        this.tenantRepository = tenantRepository;
        this.etaProvider = etaProvider != null ? etaProvider : new HaversineEtaProvider(properties, trackingRepository);
        this.properties = properties != null ? properties : new TrackingProperties();
        this.trackingMapper = trackingMapper != null ? trackingMapper : new DeliveryTrackingMapper(this.properties);
        this.currentUserProvider = currentUserProvider;
        this.tenantAccessService = tenantAccessService;
        this.currentCustomerResolver = currentCustomerResolver;
    }

    @Override
    @Transactional
    public DeliveryTrackingResponse updateDriverLocation(
            UUID tenantId,
            UUID orderId,
            DriverLocationUpdateRequest request
    ) {
        if (request == null) {
            throw new BadRequestException("Los datos de ubicación son obligatorios");
        }
        Point currentPoint = GeoUtils.createPoint(request.latitude(), request.longitude());

        Order order = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada con ID: " + orderId));

        if (order.getDeliveryType() != DeliveryType.DELIVERY) {
            throw new BadRequestException("El rastreo GPS solo aplica para pedidos con modalidad DELIVERY");
        }
        if (order.getStatus() != OrderStatus.EN_CAMINO) {
            throw new BadRequestException(
                    "Solo se puede reportar ubicación en tiempo real cuando el pedido se encuentra EN_CAMINO (estado actual: "
                            + order.getStatus() + ")"
            );
        }
        if (order.getDeliveryStaff() == null) {
            throw new BadRequestException("El pedido aún no tiene un repartidor asignado");
        }

        validateDriverPermission(tenantId, order);

        OffsetDateTime now = OffsetDateTime.now();
        DeliveryTracking existing = trackingRepository.findByOrderIdAndTenantId(orderId, tenantId).orElse(null);

        if (existing != null && existing.getRecordedAt() != null) {
            long elapsedSeconds = Duration.between(existing.getRecordedAt(), now).getSeconds();
            if (elapsedSeconds >= 0 && elapsedSeconds < properties.getMinPingIntervalSeconds()) {
                // Throttling suave: no se escribe en base de datos si el ping llega antes del intervalo mínimo
                return trackingMapper.toResponse(order, existing, Collections.emptyList(), true, true);
            }

            if (existing.getLocation() != null && elapsedSeconds > 0) {
                double jumpKm = GeoUtils.calculateDistanceKm(existing.getLocation(), currentPoint);
                double impliedSpeedKmh = (jumpKm / elapsedSeconds) * 3600.0;
                // Solo rechazamos si el salto supera 0.25 km (para tolerar jitter GPS en reposo) y excede la velocidad máxima
                if (jumpKm > 0.25 && impliedSpeedKmh > properties.getMaxPlausibleSpeedKmh()) {
                    throw new BadRequestException(String.format(
                            "Coordenadas GPS rechazadas por salto inverosímil (%.2f km en %d s ≈ %.0f km/h)",
                            jumpKm, elapsedSeconds, impliedSpeedKmh
                    ));
                }
            }
        }

        BigDecimal remainingDistanceKm = order.getEstimatedDistanceKm();
        Integer etaMinutes = order.getEstimatedDeliveryMinutes();

        if (order.getDeliveryLocation() != null) {
            EtaEstimate estimate = etaProvider.estimateRemainingFromDriver(
                    currentPoint,
                    order.getDeliveryLocation(),
                    request.speedKmh()
            );
            remainingDistanceKm = estimate.estimatedRouteDistanceKm();
            etaMinutes = estimate.estimatedMinutes();

            order.setEstimatedDistanceKm(remainingDistanceKm);
            order.setEstimatedDeliveryMinutes(etaMinutes);
            order.setEstimatedArrivalAt(estimate.estimatedArrivalAt());
            orderRepository.save(order);
        }

        User driver = order.getDeliveryStaff();
        DeliveryTracking tracking = existing != null
                ? existing
                : DeliveryTracking.builder()
                        .orderId(order.getId())
                        .order(order)
                        .tenant(order.getTenant())
                        .build();

        tracking.setDriver(driver);
        tracking.setLocation(currentPoint);
        tracking.setHeading(request.heading());
        tracking.setSpeedKmh(request.speedKmh());
        tracking.setAccuracyM(request.accuracyM());
        tracking.setRemainingDistanceKm(remainingDistanceKm);
        tracking.setEtaMinutes(etaMinutes);
        tracking.setIsActive(true);
        tracking.setRecordedAt(now);
        tracking.setUpdatedAt(now);

        DeliveryTracking savedTracking = trackingRepository.save(tracking);

        DeliveryLocationHistory historyEntry = DeliveryLocationHistory.builder()
                .order(order)
                .tenant(order.getTenant())
                .driver(driver)
                .location(currentPoint)
                .heading(request.heading())
                .speedKmh(request.speedKmh())
                .accuracyM(request.accuracyM())
                .recordedAt(now)
                .build();
        historyRepository.save(historyEntry);

        return trackingMapper.toResponse(order, savedTracking, Collections.emptyList(), true, false);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryTrackingResponse getOrderTrackingForStaff(
            UUID tenantId,
            UUID orderId,
            boolean includeHistory
    ) {
        Order order = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada con ID: " + orderId));

        if (order.getDeliveryType() != DeliveryType.DELIVERY) {
            throw new BadRequestException("Este pedido no es de modalidad DELIVERY");
        }

        DeliveryTracking tracking = trackingRepository.findByOrderIdAndTenantId(orderId, tenantId).orElse(null);
        List<DeliveryLocationHistory> history = includeHistory
                ? historyRepository.findAllByOrderIdAndTenantIdOrderByRecordedAtAsc(orderId, tenantId)
                : Collections.emptyList();

        return trackingMapper.toResponse(order, tracking, history, true, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryTrackingResponse> getActiveTrackingsByTenant(UUID tenantId) {
        return trackingRepository.findAllByTenantIdAndIsActiveTrueOrderByUpdatedAtDesc(tenantId)
                .stream()
                .filter(t -> t.getOrder() != null && t.getOrder().getStatus() == OrderStatus.EN_CAMINO)
                .map(t -> trackingMapper.toResponse(t.getOrder(), t, Collections.emptyList(), true, false))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryTrackingResponse getOrderTrackingForCustomer(String slug, UUID orderId) {
        TenantCustomer customer = currentCustomerResolver.resolveBySlug(slug);
        UUID tenantId = customer.getTenant().getId();
        UUID userId = customer.getUser() != null && customer.getUser().getId() != null
                ? customer.getUser().getId()
                : customer.getId().getUserId();

        Order order = orderRepository.findByIdAndTenantIdAndCustomerUserId(orderId, tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado en tu historial"));

        if (order.getDeliveryType() != DeliveryType.DELIVERY) {
            throw new BadRequestException("El seguimiento de reparto solo está disponible para pedidos a domicilio (DELIVERY)");
        }

        DeliveryTracking tracking = trackingRepository.findByOrderIdAndTenantId(orderId, tenantId).orElse(null);

        // Privacidad: el cliente solo ve las coordenadas en vivo del repartidor mientras el pedido está EN_CAMINO
        boolean canSeeLiveDriverCoords = order.getStatus() == OrderStatus.EN_CAMINO
                && tracking != null
                && Boolean.TRUE.equals(tracking.getIsActive());

        return trackingMapper.toResponse(
                order,
                tracking,
                Collections.emptyList(),
                canSeeLiveDriverCoords,
                false
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryEstimateResponse estimateDeliveryBySlug(String slug, Double latitude, Double longitude) {
        Point customerPoint = GeoUtils.createPoint(latitude, longitude);

        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado con slug: " + slug));

        if (Boolean.FALSE.equals(tenant.getIsActive())) {
            throw new BadRequestException("El restaurante '" + tenant.getName() + "' no se encuentra activo actualmente");
        }
        if (tenant.getLocation() == null) {
            throw new BadRequestException("El restaurante aún no tiene configurada su ubicación geográfica");
        }

        EtaEstimate estimate = etaProvider.estimateFromStoreToCustomer(tenant.getLocation(), customerPoint);
        BigDecimal maxRadius = tenant.getDeliveryRadiusKm();
        boolean withinCoverage = maxRadius == null
                || estimate.straightDistanceKm().compareTo(maxRadius) <= 0;

        BigDecimal deliveryFee = tenant.getDefaultDeliveryFee() != null
                ? tenant.getDefaultDeliveryFee().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        String message = withinCoverage
                ? estimate.message()
                : String.format(
                        "Tu ubicación está fuera de la zona de cobertura del restaurante (%.2f km > %.2f km máx.)",
                        estimate.straightDistanceKm(),
                        maxRadius
                );

        return DeliveryEstimateResponse.builder()
                .tenantId(tenant.getId())
                .tenantSlug(tenant.getSlug())
                .tenantName(tenant.getName())
                .withinCoverage(withinCoverage)
                .maxDeliveryRadiusKm(maxRadius)
                .straightDistanceKm(estimate.straightDistanceKm())
                .estimatedRouteDistanceKm(estimate.estimatedRouteDistanceKm())
                .deliveryFee(deliveryFee)
                .estimatedDeliveryMinutes(withinCoverage ? estimate.estimatedMinutes() : null)
                .minMinutes(withinCoverage ? estimate.minMinutes() : null)
                .maxMinutes(withinCoverage ? estimate.maxMinutes() : null)
                .estimatedArrivalAt(withinCoverage ? estimate.estimatedArrivalAt() : null)
                .message(message)
                .build();
    }

    @Override
    @Transactional
    public void onOrderDispatched(Order order) {
        if (order == null || order.getDeliveryType() != DeliveryType.DELIVERY || order.getDeliveryStaff() == null) {
            return;
        }
        Tenant tenant = order.getTenant();
        Point initialLocation = tenant != null && tenant.getLocation() != null
                ? tenant.getLocation()
                : order.getDeliveryLocation();

        if (initialLocation == null) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        DeliveryTracking tracking = trackingRepository
                .findByOrderIdAndTenantId(order.getId(), tenant.getId())
                .orElseGet(() -> DeliveryTracking.builder()
                        .orderId(order.getId())
                        .order(order)
                        .tenant(tenant)
                        .location(initialLocation)
                        .recordedAt(now)
                        .build());

        tracking.setDriver(order.getDeliveryStaff());
        if (tracking.getLocation() == null) {
            tracking.setLocation(initialLocation);
        }
        tracking.setRemainingDistanceKm(order.getEstimatedDistanceKm());
        tracking.setEtaMinutes(order.getEstimatedDeliveryMinutes());
        tracking.setIsActive(true);
        tracking.setUpdatedAt(now);
        if (tracking.getRecordedAt() == null) {
            tracking.setRecordedAt(now);
        }
        trackingRepository.save(tracking);
    }

    @Override
    @Transactional
    public void onOrderClosed(Order order) {
        if (order == null || order.getId() == null || order.getTenant() == null) {
            return;
        }
        trackingRepository.findByOrderIdAndTenantId(order.getId(), order.getTenant().getId())
                .ifPresent(tracking -> {
                    tracking.setIsActive(false);
                    tracking.setRemainingDistanceKm(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    tracking.setEtaMinutes(0);
                    tracking.setUpdatedAt(OffsetDateTime.now());
                    trackingRepository.save(tracking);
                });
    }

    private void validateDriverPermission(UUID tenantId, Order order) {
        if (currentUserProvider == null) {
            return;
        }
        UUID actorUserId;
        try {
            actorUserId = currentUserProvider.getCurrentUserId();
        } catch (Exception e) {
            return;
        }
        if (actorUserId == null) {
            return;
        }

        boolean isSuperAdmin = tenantAccessService != null && tenantAccessService.isSuperAdmin();
        if (isSuperAdmin) {
            return;
        }

        TenantStaffRole role = tenantAccessService != null
                ? tenantAccessService.getCurrentUserRoleInTenant(tenantId).orElse(null)
                : null;

        if (role == TenantStaffRole.OWNER || role == TenantStaffRole.ADMIN) {
            return;
        }

        if (!actorUserId.equals(order.getDeliveryStaff().getId())) {
            throw new ForbiddenException("Solo el repartidor asignado a este pedido puede actualizar su ubicación");
        }
    }
}

