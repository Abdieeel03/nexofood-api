package lat.nexofood.api.modules.inventory.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.modules.inventory.domain.InventoryItem;
import lat.nexofood.api.modules.inventory.domain.InventoryUnit;
import lat.nexofood.api.modules.inventory.infrastructure.repository.InventoryItemRepository;
import lat.nexofood.api.modules.inventory.infrastructure.repository.InventoryStockRepository;
import lat.nexofood.api.modules.inventory.infrastructure.repository.ProductIngredientRepository;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryItemRequest;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryItemResponse;
import lat.nexofood.api.modules.inventory.web.mapper.InventoryItemMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryItemServiceTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private InventoryStockRepository inventoryStockRepository;
    @Mock
    private ProductIngredientRepository productIngredientRepository;
    @Mock
    private TenantRepository tenantRepository;

    private InventoryItemServiceImpl inventoryItemService;
    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        inventoryItemService = new InventoryItemServiceImpl(
                inventoryItemRepository,
                inventoryStockRepository,
                productIngredientRepository,
                tenantRepository,
                new InventoryItemMapper()
        );
        tenantId = UUID.randomUUID();
        tenant = Tenant.builder().id(tenantId).name("Pizzería Bella Napoli").build();
    }

    @Test
    @DisplayName("create: registra un nuevo insumo cuando el nombre y SKU son únicos en el restaurante")
    void createItemSuccessfully() {
        InventoryItemRequest request = InventoryItemRequest.builder()
                .name("  Harina de Trigo 00  ")
                .sku("  INS-HAR-01  ")
                .category("Secos")
                .unit(InventoryUnit.KG)
                .costPrice(new BigDecimal("4.50"))
                .isActive(true)
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(inventoryItemRepository.existsByTenantIdAndNameIgnoreCase(tenantId, "Harina de Trigo 00")).thenReturn(false);
        when(inventoryItemRepository.existsByTenantIdAndSkuIgnoreCase(tenantId, "INS-HAR-01")).thenReturn(false);
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> {
            InventoryItem item = inv.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });

        InventoryItemResponse response = inventoryItemService.create(tenantId, request);

        assertNotNull(response.id());
        assertEquals("Harina de Trigo 00", response.name());
        assertEquals("INS-HAR-01", response.sku());
        assertEquals(InventoryUnit.KG, response.unit());
        assertEquals(new BigDecimal("4.50"), response.costPrice());
        assertTrue(response.isActive());
    }

    @Test
    @DisplayName("create: rechaza SKU duplicado en el mismo restaurante con 409 Conflict")
    void createItemRejectsDuplicateSku() {
        InventoryItemRequest request = InventoryItemRequest.builder()
                .name("Queso Mozzarella")
                .sku("INS-HAR-01")
                .unit(InventoryUnit.KG)
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(inventoryItemRepository.existsByTenantIdAndNameIgnoreCase(tenantId, "Queso Mozzarella")).thenReturn(false);
        when(inventoryItemRepository.existsByTenantIdAndSkuIgnoreCase(tenantId, "INS-HAR-01")).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> inventoryItemService.create(tenantId, request));
        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("toggleStatus y delete: bloquea eliminación si el insumo ya tiene un registro de stock")
    void toggleAndDeleteItemRules() {
        UUID itemId = UUID.randomUUID();
        InventoryItem item = InventoryItem.builder()
                .id(itemId)
                .tenant(tenant)
                .name("Harina")
                .sku("INS-HAR-01")
                .unit(InventoryUnit.KG)
                .isActive(true)
                .build();

        when(inventoryItemRepository.findByIdAndTenantId(itemId, tenantId)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryItemResponse toggled = inventoryItemService.toggleStatus(tenantId, itemId, false);
        assertFalse(toggled.isActive());

        when(inventoryStockRepository.existsByTenantIdAndItemId(tenantId, itemId)).thenReturn(true);
        assertThrows(BadRequestException.class, () -> inventoryItemService.delete(tenantId, itemId));
        verify(inventoryItemRepository, never()).delete(any(InventoryItem.class));
    }
}
