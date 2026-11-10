package lat.nexofood.api.modules.tracking.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.tracking")
@Getter
@Setter
public class TrackingProperties {

    /** Velocidad promedio estimada del repartidor en ciudad (km/h). */
    private double averageSpeedKmh = 25.0;

    /** Factor multiplicador de ruta vial frente a la distancia en línea recta. */
    private double roadFactor = 1.35;

    /** Minutos adicionales al asignar repartidor (recoger pedido y salir del local). */
    private int pickupBufferMinutes = 3;

    /** Minutos adicionales de entrega final (estacionar, tocar timbre y entregar). */
    private int dropoffBufferMinutes = 2;

    /** Mínimo de minutos de ETA reportado mientras el pedido sigue en camino. */
    private int minEtaMinutes = 2;

    /** Intervalo mínimo permitido entre pings consecutivos del repartidor (segundos). */
    private int minPingIntervalSeconds = 5;

    /** Intervalo sugerido al frontend del cliente para consultar la posición (segundos). */
    private int pollIntervalSeconds = 15;

    /** Segundos tras los cuales una posición se considera desactualizada (stale). */
    private int staleAfterSeconds = 60;

    /** Velocidad máxima plausible entre dos puntos para detectar saltos GPS inválidos (km/h). */
    private double maxPlausibleSpeedKmh = 150.0;

    /** Días de retención del historial de posiciones antes de ser purgado por el job. */
    private int historyRetentionDays = 7;
}

