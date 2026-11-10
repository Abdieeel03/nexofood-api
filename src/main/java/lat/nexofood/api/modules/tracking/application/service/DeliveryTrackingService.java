package lat.nexofood.api.modules.tracking.application.service;

import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.tracking.web.dto.request.DriverLocationUpdateRequest;
import lat.nexofood.api.modules.tracking.web.dto.response.DeliveryEstimateResponse;
import lat.nexofood.api.modules.tracking.web.dto.response.DeliveryTrackingResponse;

import java.util.List;
import java.util.UUID;

public interface DeliveryTrackingService {

    /**
     * Registra un ping de coordenadas GPS enviado por el repartidor asignado a un pedido en estado EN_CAMINO,
     * recalcula el ETA restante hacia el domicilio del cliente y actualiza la sesión activa.
     */
    DeliveryTrackingResponse updateDriverLocation(UUID tenantId, UUID orderId, DriverLocationUpdateRequest request);

    /**
     * Obtiene el estado de rastreo completo de un pedido para el personal del restaurante
     * (opcionalmente incluyendo el historial de puntos del recorrido).
     */
    DeliveryTrackingResponse getOrderTrackingForStaff(UUID tenantId, UUID orderId, boolean includeHistory);

    /**
     * Lista todas las entregas en curso (activas) del restaurante con la última posición de cada repartidor.
     */
    List<DeliveryTrackingResponse> getActiveTrackingsByTenant(UUID tenantId);

    /**
     * Obtiene el estado de rastreo de un pedido para el cliente propietario en el storefront.
     * Solo expone las coordenadas en vivo del repartidor mientras el pedido se encuentra EN_CAMINO.
     */
    DeliveryTrackingResponse getOrderTrackingForCustomer(String slug, UUID orderId);

    /**
     * Calcula una estimación pública de cobertura, tarifa de envío, distancia y ETA entre la tienda
     * y unas coordenadas dadas antes de realizar el checkout.
     */
    DeliveryEstimateResponse estimateDeliveryBySlug(String slug, Double latitude, Double longitude);

    /**
     * Inicializa o reactiva la sesión de rastreo cuando el pedido pasa a EN_CAMINO
     * (posicionando inicialmente al repartidor en la ubicación de la tienda si aún no hay pings).
     */
    void onOrderDispatched(Order order);

    /**
     * Cierra la sesión de rastreo cuando el pedido pasa a ENTREGADO o CANCELADO.
     */
    void onOrderClosed(Order order);
}

