package lat.nexofood.api.modules.catalog.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.store.domain.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductStockServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductStockServiceImpl productStockService;
    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        productStockService = new ProductStockServiceImpl(productRepository);
        tenantId = UUID.randomUUID();
        tenant = Tenant.builder().id(tenantId).name("Burger Bros").build();
    }

    @Test
    @DisplayName("Dominio Product: hasSufficientStock, decreaseStock, increaseStock y updateStock validan límites")
    void domainStockMethodsEnforceValidations() {
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name("Hamburguesa Clásica")
                .stock(5)
                .isAvailable(true)
                .build();

        assertTrue(product.hasSufficientStock(5));
        assertFalse(product.hasSufficientStock(6));
        assertFalse(product.hasSufficientStock(0));

        product.decreaseStock(3);
        assertEquals(2, product.getStock());

        assertThrows(BadRequestException.class, () -> product.decreaseStock(3));
        assertThrows(BadRequestException.class, () -> product.decreaseStock(0));

        product.increaseStock(4);
        assertEquals(6, product.getStock());
        assertThrows(BadRequestException.class, () -> product.increaseStock(0));

        product.updateStock(0);
        assertEquals(0, product.getStock());
        assertThrows(BadRequestException.class, () -> product.updateStock(-1));
    }

    @Test
    @DisplayName("consumeStock: ordena bloqueos por UUID ascendente y descuenta stock correctamente")
    void consumeStockOrdersLocksAndDecrementsStock() {
        UUID smallerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID largerId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        Product prodSmall = Product.builder()
                .id(smallerId).tenant(tenant).name("Agua Mineral").stock(10).isAvailable(true).build();
        Product prodLarge = Product.builder()
                .id(largerId).tenant(tenant).name("Pizza Americana").stock(8).isAvailable(true).build();

        when(productRepository.findByIdAndTenantIdForUpdate(smallerId, tenantId)).thenReturn(Optional.of(prodSmall));
        when(productRepository.findByIdAndTenantIdForUpdate(largerId, tenantId)).thenReturn(Optional.of(prodLarge));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // Insertamos primero el UUID mayor para comprobar el reordenamiento determinístico
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        quantities.put(largerId, 3);
        quantities.put(smallerId, 2);

        productStockService.consumeStock(tenantId, quantities);

        InOrder inOrder = inOrder(productRepository);
        inOrder.verify(productRepository).findByIdAndTenantIdForUpdate(smallerId, tenantId);
        inOrder.verify(productRepository).findByIdAndTenantIdForUpdate(largerId, tenantId);

        assertEquals(8, prodSmall.getStock());
        assertEquals(5, prodLarge.getStock());
    }

    @Test
    @DisplayName("consumeStock: rechaza producto no disponible o con stock insuficiente")
    void consumeStockRejectsUnavailableOrInsufficientStock() {
        UUID productId = UUID.randomUUID();
        Product unavailable = Product.builder()
                .id(productId).tenant(tenant).name("Gaseosa").stock(10).isAvailable(false).build();

        when(productRepository.findByIdAndTenantIdForUpdate(productId, tenantId)).thenReturn(Optional.of(unavailable));

        assertThrows(BadRequestException.class, () ->
                productStockService.consumeStock(tenantId, Map.of(productId, 1))
        );

        unavailable.setIsAvailable(true);
        unavailable.setStock(2);
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                productStockService.consumeStock(tenantId, Map.of(productId, 5))
        );
        assertTrue(ex.getMessage().contains("Stock insuficiente"));
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("restoreStock: incrementa el stock del producto al cancelar un pedido")
    void restoreStockIncrementsProductStock() {
        UUID productId = UUID.randomUUID();
        Product burger = Product.builder()
                .id(productId).tenant(tenant).name("Burger Doble").stock(4).isAvailable(true).build();

        when(productRepository.findByIdAndTenantIdForUpdate(productId, tenantId)).thenReturn(Optional.of(burger));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productStockService.restoreStock(tenantId, Map.of(productId, 3));

        assertEquals(7, burger.getStock());
        verify(productRepository).save(burger);
    }
}
