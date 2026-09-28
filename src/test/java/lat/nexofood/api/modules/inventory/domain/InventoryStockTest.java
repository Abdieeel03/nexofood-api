package lat.nexofood.api.modules.inventory.domain;

import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.store.domain.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InventoryStockTest {

    @Test
    @DisplayName("Should create InventoryItem and InventoryStock correctly without redundant unit")
    void shouldCreateItemAndStockCorrectly() {
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).name("Pizzeria").build();

        InventoryItem item = InventoryItem.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name("Queso Mozzarella")
                .sku("ING-MOZZ-01")
                .unit(InventoryUnit.KG)
                .costPrice(new BigDecimal("22.50"))
                .build();

        InventoryStock stock = InventoryStock.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .item(item)
                .quantity(new BigDecimal("50.000"))
                .reservedQuantity(new BigDecimal("5.000"))
                .minimumStock(new BigDecimal("10.000"))
                .location("Cámara Fría 1")
                .build();

        assertEquals(tenant, stock.getTenant());
        assertEquals(item, stock.getItem());
        assertEquals(new BigDecimal("50.000"), stock.getQuantity());
        assertEquals(new BigDecimal("5.000"), stock.getReservedQuantity());
        assertEquals(new BigDecimal("10.000"), stock.getMinimumStock());
        assertEquals("Cámara Fría 1", stock.getLocation());
        // Verify delegated unit from item
        assertEquals(InventoryUnit.KG, stock.getUnit());
    }

    @Test
    @DisplayName("Should create InventoryMovement referencing InventoryStock and InventoryItem")
    void shouldCreateInventoryMovementCorrectly() {
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).name("Pizzeria").build();
        User user = User.builder().id(UUID.randomUUID()).fullName("Chef Juan").build();

        InventoryItem item = InventoryItem.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name("Harina de Trigo")
                .unit(InventoryUnit.KG)
                .build();

        InventoryStock stock = InventoryStock.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .item(item)
                .quantity(new BigDecimal("100.000"))
                .build();

        InventoryMovement movement = InventoryMovement.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .stock(stock)
                .item(item)
                .movementType(InventoryMovementType.EXIT)
                .reason(InventoryMovementReason.KITCHEN_CONSUMPTION_OR_WASTE)
                .quantity(new BigDecimal("10.000"))
                .previousQuantity(new BigDecimal("100.000"))
                .newQuantity(new BigDecimal("90.000"))
                .performedBy(user)
                .performedByName(user.getFullName())
                .reasonDetails("Consumo diario para pizzas")
                .build();

        assertNotNull(movement);
        assertEquals(stock, movement.getStock());
        assertEquals(item, movement.getItem());
        assertEquals(tenant, movement.getTenant());
        assertEquals(InventoryMovementType.EXIT, movement.getMovementType());
        assertEquals(new BigDecimal("90.000"), movement.getNewQuantity());
    }
}
