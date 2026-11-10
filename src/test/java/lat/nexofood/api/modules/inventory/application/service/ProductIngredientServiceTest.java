package lat.nexofood.api.modules.inventory.application.service;

import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.inventory.domain.InventoryItem;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementReason;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementType;
import lat.nexofood.api.modules.inventory.domain.InventoryStock;
import lat.nexofood.api.modules.inventory.domain.InventoryUnit;
import lat.nexofood.api.modules.inventory.domain.ProductIngredient;
import lat.nexofood.api.modules.inventory.infrastructure.repository.InventoryItemRepository;
import lat.nexofood.api.modules.inventory.infrastructure.repository.InventoryMovementRepository;
import lat.nexofood.api.modules.inventory.infrastructure.repository.InventoryStockRepository;
import lat.nexofood.api.modules.inventory.infrastructure.repository.ProductIngredientRepository;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryMovementRequest;
import lat.nexofood.api.modules.inventory.web.mapper.ProductIngredientMapper;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderItem;
import lat.nexofood.api.modules.store.domain.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductIngredientServiceTest {

    @Mock
    private ProductIngredientRepository productIngredientRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private InventoryStockRepository inventoryStockRepository;
    @Mock
    private InventoryMovementRepository inventoryMovementRepository;
    @Mock
    private InventoryMovementService inventoryMovementService;

    private ProductIngredientServiceImpl productIngredientService;
    private UUID tenantId;
    private Tenant tenant;
    private Product pizzaProduct;
    private InventoryItem cheeseItem;
    private InventoryStock cheeseStock;

    @BeforeEach
    void setUp() {
        productIngredientService = new ProductIngredientServiceImpl(
                productIngredientRepository,
                productRepository,
                inventoryItemRepository,
                inventoryStockRepository,
                inventoryMovementRepository,
                inventoryMovementService,
                new ProductIngredientMapper()
        );
        tenantId = UUID.randomUUID();
        tenant = Tenant.builder().id(tenantId).name("Pizzería Bella Napoli").build();
        pizzaProduct = Product.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name("Pizza Margherita")
                .isAvailable(true)
                .build();
        cheeseItem = InventoryItem.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name("Queso Mozzarella")
                .unit(InventoryUnit.KG)
                .isActive(true)
                .build();
        cheeseStock = InventoryStock.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .item(cheeseItem)
                .quantity(new BigDecimal("20.000"))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("consumeStockForOrder y restoreStockForCanceledOrder: registra EXIT/SALE al preparar y ENTRY/CUSTOMER_RETURN al cancelar")
    void consumeAndRestoreStockForOrderWithRecipe() {
        ProductIngredient recipeItem = ProductIngredient.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .product(pizzaProduct)
                .inventoryItem(cheeseItem)
                .quantity(new BigDecimal("0.250"))
                .build();

        OrderItem orderItem = OrderItem.builder()
                .id(UUID.randomUUID())
                .product(pizzaProduct)
                .productName("Pizza Margherita")
                .quantity(2)
                .build();

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .orderNumber("ORD-000001")
                .items(List.of(orderItem))
                .build();

        when(inventoryMovementRepository.existsByTenantIdAndReasonDetailsStartingWithAndMovementType(
                tenantId, "Descuento automático por orden ORD-000001", InventoryMovementType.EXIT
        )).thenReturn(false);
        when(productIngredientRepository.findAllByTenantIdAndProductId(tenantId, pizzaProduct.getId()))
                .thenReturn(List.of(recipeItem));
        when(inventoryStockRepository.findByTenantIdAndItemId(tenantId, cheeseItem.getId()))
                .thenReturn(Optional.of(cheeseStock));

        productIngredientService.consumeStockForOrder(order);

        ArgumentCaptor<InventoryMovementRequest> exitCaptor = ArgumentCaptor.forClass(InventoryMovementRequest.class);
        verify(inventoryMovementService).register(eq(tenantId), exitCaptor.capture());

        InventoryMovementRequest capturedExit = exitCaptor.getValue();
        assertEquals(cheeseStock.getId(), capturedExit.stockId());
        assertEquals(InventoryMovementType.EXIT, capturedExit.movementType());
        assertEquals(InventoryMovementReason.SALE, capturedExit.reason());
        assertEquals(new BigDecimal("0.500"), capturedExit.quantity()); // 0.250 * 2 pizzas

        // Ahora probamos el reverso al cancelar la orden
        when(inventoryMovementRepository.existsByTenantIdAndReasonDetailsStartingWithAndMovementType(
                tenantId, "Reverso automático por cancelación de orden ORD-000001", InventoryMovementType.ENTRY
        )).thenReturn(false);

        productIngredientService.restoreStockForCanceledOrder(order);

        ArgumentCaptor<InventoryMovementRequest> entryCaptor = ArgumentCaptor.forClass(InventoryMovementRequest.class);
        verify(inventoryMovementService, org.mockito.Mockito.times(2)).register(eq(tenantId), entryCaptor.capture());

        InventoryMovementRequest capturedEntry = entryCaptor.getAllValues().get(1);
        assertEquals(InventoryMovementType.ENTRY, capturedEntry.movementType());
        assertEquals(InventoryMovementReason.CUSTOMER_RETURN, capturedEntry.reason());
        assertEquals(new BigDecimal("0.500"), capturedEntry.quantity());
    }
}
