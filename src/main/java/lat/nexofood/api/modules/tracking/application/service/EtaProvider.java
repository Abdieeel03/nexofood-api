package lat.nexofood.api.modules.tracking.application.service;

import org.locationtech.jts.geom.Point;

/**
 * Proveedor de cálculo de distancia y tiempo estimado de entrega (ETA).
 * Diseñado como estrategia intercambiable para permitir enchufar Google Routes / Directions API
 * u OSRM en el futuro sin modificar la lógica de pedidos ni de rastreo.
 */
public interface EtaProvider {

    /**
     * Calcula el ETA inicial entre el local del restaurante y la dirección del cliente
     * (incluye buffer de recogida + buffer de entrega).
     */
    EtaEstimate estimateFromStoreToCustomer(Point storeLocation, Point customerLocation);

    /**
     * Calcula el ETA restante en vivo desde la posición actual del repartidor hasta la casa del cliente
     * (incluye únicamente el buffer de entrega final).
     */
    EtaEstimate estimateRemainingFromDriver(Point driverLocation, Point customerLocation, Double currentSpeedKmh);
}

