package lat.nexofood.api.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

    @Test
    @DisplayName("Debe inicializar los caches personalizados con Caffeine")
    void shouldInitializeConfiguredCaches() {
        CacheConfig config = new CacheConfig();
        CacheManager cacheManager = config.cacheManager();

        assertThat(cacheManager).isNotNull();

        Cache menuCache = cacheManager.getCache(CacheConfig.CACHE_STOREFRONT_MENU);
        Cache catCache = cacheManager.getCache(CacheConfig.CACHE_STOREFRONT_CATEGORIES);
        Cache planCache = cacheManager.getCache(CacheConfig.CACHE_SUBSCRIPTION_PLANS);

        assertThat(menuCache).isNotNull();
        assertThat(catCache).isNotNull();
        assertThat(planCache).isNotNull();

        // Verificar comportamiento básico de inserción, recuperación y desalojo
        menuCache.put("pizzeria-don-tito", "mock-menu-data");
        assertThat(menuCache.get("pizzeria-don-tito", String.class)).isEqualTo("mock-menu-data");

        menuCache.evict("pizzeria-don-tito");
        assertThat(menuCache.get("pizzeria-don-tito")).isNull();
    }
}
