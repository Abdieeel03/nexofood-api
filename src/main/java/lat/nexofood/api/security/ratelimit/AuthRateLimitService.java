package lat.nexofood.api.security.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de limitación de tasa (Rate Limiting) basado en Bucket4j (algoritmo Token Bucket)
 * para proteger los endpoints de autenticación contra ataques de fuerza bruta y enumeración.
 */
@Service
public class AuthRateLimitService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.security.rate-limit.capacity:10}")
    private int capacity = 10;

    @Value("${app.security.rate-limit.refill-seconds:60}")
    private long refillSeconds = 60L;

    public AuthRateLimitService() {
    }

    public AuthRateLimitService(int capacity, long refillSeconds) {
        this.capacity = capacity;
        this.refillSeconds = refillSeconds;
    }

    public Bucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, this::newBucket);
    }

    public ConsumptionProbe tryConsume(String key) {
        return resolveBucket(key).tryConsumeAndReturnRemaining(1);
    }

    public void clearAll() {
        buckets.clear();
    }

    private Bucket newBucket(String key) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofSeconds(refillSeconds))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
