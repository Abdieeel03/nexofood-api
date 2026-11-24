package lat.nexofood.api.modules.catalog.application.service;

import java.util.Map;
import java.util.UUID;

public interface ProductStockService {

    void consumeStock(UUID tenantId, Map<UUID, Integer> quantitiesByProductId);

    void restoreStock(UUID tenantId, Map<UUID, Integer> quantitiesByProductId);
}
