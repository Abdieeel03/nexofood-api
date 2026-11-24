package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.notification.application.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class ProductStockServiceImpl implements ProductStockService {

    private final ProductRepository productRepository;
    private final NotificationService notificationService;

    @Autowired
    public ProductStockServiceImpl(
            ProductRepository productRepository,
            @Autowired(required = false) NotificationService notificationService
    ) {
        this.productRepository = productRepository;
        this.notificationService = notificationService;
    }

    public ProductStockServiceImpl(ProductRepository productRepository) {
        this(productRepository, null);
    }

    @Override
    @CacheEvict(value = "storefront_menu", allEntries = true)
    @Transactional
    public void consumeStock(UUID tenantId, Map<UUID, Integer> quantitiesByProductId) {
        if (tenantId == null || quantitiesByProductId == null || quantitiesByProductId.isEmpty() || productRepository == null) {
            return;
        }

        // Bloquear en orden lexicográfico ascendente de UUID para prevenir deadlocks ante órdenes concurrentes
        Map<UUID, Integer> orderedQuantities = new TreeMap<>(Comparator.comparing(UUID::toString));
        orderedQuantities.putAll(quantitiesByProductId);
        for (Map.Entry<UUID, Integer> entry : orderedQuantities.entrySet()) {
            UUID productId = entry.getKey();
            Integer quantity = entry.getValue();
            if (productId == null || quantity == null || quantity <= 0) {
                continue;
            }

            Product product = productRepository.findByIdAndTenantIdForUpdate(productId, tenantId)
                    .or(() -> productRepository.findByIdAndTenantId(productId, tenantId))
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));

            if (!Boolean.TRUE.equals(product.getIsAvailable())) {
                throw new BadRequestException("El producto '" + product.getName() + "' no está disponible actualmente.");
            }

            product.decreaseStock(quantity);
            Product savedProduct = productRepository.save(product);
            if (notificationService != null) {
                notificationService.checkAndNotifyProductStock(savedProduct != null ? savedProduct : product);
            }
        }
    }

    @Override
    @CacheEvict(value = "storefront_menu", allEntries = true)
    @Transactional
    public void restoreStock(UUID tenantId, Map<UUID, Integer> quantitiesByProductId) {
        if (tenantId == null || quantitiesByProductId == null || quantitiesByProductId.isEmpty() || productRepository == null) {
            return;
        }

        Map<UUID, Integer> orderedQuantities = new TreeMap<>(Comparator.comparing(UUID::toString));
        orderedQuantities.putAll(quantitiesByProductId);
        for (Map.Entry<UUID, Integer> entry : orderedQuantities.entrySet()) {
            UUID productId = entry.getKey();
            Integer quantity = entry.getValue();
            if (productId == null || quantity == null || quantity <= 0) {
                continue;
            }

            productRepository.findByIdAndTenantIdForUpdate(productId, tenantId)
                    .or(() -> productRepository.findByIdAndTenantId(productId, tenantId))
                    .ifPresent(product -> {
                        product.increaseStock(quantity);
                        productRepository.save(product);
                    });
        }
    }
}
