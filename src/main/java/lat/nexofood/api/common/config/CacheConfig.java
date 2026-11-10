package lat.nexofood.api.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuración de caché en memoria de alto rendimiento basada en Caffeine.
 * Optimiza las lecturas públicas concurrentes (menús, catálogos y planes)
 * evitando sobrecargar la base de datos de Supabase en picos de tráfico.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_STOREFRONT_MENU = "storefront_menu";
    public static final String CACHE_STOREFRONT_CATEGORIES = "storefront_categories";
    public static final String CACHE_SUBSCRIPTION_PLANS = "subscription_plans";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // Menú público completo por slug (5 minutos de TTL, máx 500 restaurantes en RAM)
        cacheManager.registerCustomCache(
                CACHE_STOREFRONT_MENU,
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .recordStats()
                        .build()
        );

        // Categorías activas del storefront (10 minutos de TTL)
        cacheManager.registerCustomCache(
                CACHE_STOREFRONT_CATEGORIES,
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .recordStats()
                        .build()
        );

        // Catálogo de planes de suscripción (1 hora de TTL)
        cacheManager.registerCustomCache(
                CACHE_SUBSCRIPTION_PLANS,
                Caffeine.newBuilder()
                        .maximumSize(50)
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .recordStats()
                        .build()
        );

        return cacheManager;
    }
}
