package lat.nexofood.api.modules.inventory.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.modules.inventory.domain.InventoryItem;
import lat.nexofood.api.modules.inventory.domain.InventoryStock;
import lat.nexofood.api.modules.inventory.domain.InventoryUnit;
import lat.nexofood.api.modules.inventory.infrastructure.repository.InventoryItemRepository;
import lat.nexofood.api.modules.inventory.infrastructure.repository.InventoryStockRepository;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryStockRequest;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryStockUpdateRequest;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryStockResponse;
import lat.nexofood.api.modules.inventory.web.mapper.InventoryStockMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryStockServiceTest {

    @Mock
    private InventoryStockRepository inventoryStockRepository;
    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private TenantRepository tenantRepository;

    private InventoryStockServiceImpl inventoryStockService;
    private UUID tenantId;
    private Tenant tenant;
    private InventoryItem item;

    @BeforeEach
    void setUp() {
        inventoryStockService = new InventoryStockServiceImpl(
                inventoryStockRepository,
                inventoryItemRepository,
                tenantRepository,
                new InventoryStockMapper()
        );
        tenantId = UUID.randomUUID();
        tenant = Tenant.builder().id(tenantId).name("Pizzería Bella Napoli").build();
        item = InventoryItem.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name("Queso Mozzarella")
                .sku("INS-MOZ-01")
                .unit(InventoryUnit.KG)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("create: inicializa el stock en 0.000 para un insumo activo y único por restaurante")
    void createStockInitializesZeroQuantity() {
        InventoryStockRequest request = InventoryStockRequest.builder()
                .itemId(item.getId())
                .minimumStock(new BigDecimal("5.000"))
                .maximumStock(new BigDecimal("40.000"))
                .location("Cámara Fría 1")
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(inventoryItemRepository.findByIdAndTenantId(item.getId(), tenantId)).thenReturn(Optional.of(item));
        when(inventoryStockRepository.existsByTenantIdAndItemId(tenantId, item.getId())).thenReturn(false);
        when(inventoryStockRepository.save(any(InventoryStock.class))).thenAnswer(inv -> {
            InventoryStock stock = inv.getArgument(0);
            stock.setId(UUID.randomUUID());
            return stock;
        });

        InventoryStockResponse response = inventoryStockService.create(tenantId, request);

        assertEquals(new BigDecimal("0.000"), response.quantity());
        assertEquals(new BigDecimal("0.000"), response.reservedQuantity());
        assertEquals(new BigDecimal("5.000"), response.minimumStock());
        assertEquals(new BigDecimal("40.000"), response.maximumStock());
        assertTrue(response.lowStock());
        assertEquals("Cámara Fría 1", response.location());
    }

    @Test
    @DisplayName("create: rechaza duplicar registro de stock para el mismo insumo o si maximumStock < minimumStock")
    void createStockRejectsDuplicateOrInvalidThresholds() {
        InventoryStockRequest duplicateReq = InventoryStockRequest.builder()
                .itemId(item.getId())
                .minimumStock(new BigDecimal("5.000"))
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(inventoryItemRepository.findByIdAndTenantId(item.getId(), tenantId)).thenReturn(Optional.of(item));
        when(inventoryStockRepository.existsByTenantIdAndItemId(tenantId, item.getId())).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> inventoryStockService.create(tenantId, duplicateReq));

        when(inventoryStockRepository.existsByTenantIdAndItemId(tenantId, item.getId())).thenReturn(false);
        InventoryStockRequest badThresholdsReq = InventoryStockRequest.builder()
                .itemId(item.getId())
                .minimumStock(new BigDecimal("20.000"))
                .maximumStock(new BigDecimal("10.000"))
                .build();

        assertThrows(BadRequestException.class, () -> inventoryStockService.create(tenantId, badThresholdsReq));
    }

    @Test
    @DisplayName("update y findLowStockAlerts: actualiza parámetros sin tocar quantity y retorna alertas de stock crítico")
    void updateStockAndFindLowStockAlerts() {
        UUID stockId = UUID.randomUUID();
        InventoryStock stock = InventoryStock.builder()
                .id(stockId)
                .tenant(tenant)
                .item(item)
                .quantity(new BigDecimal("4.000"))
                .reservedQuantity(BigDecimal.ZERO.setScale(3))
                .minimumStock(new BigDecimal("5.000"))
                .location("Cámara Fría 1")
                .isActive(true)
                .build();

        when(inventoryStockRepository.findByIdAndTenantId(stockId, tenantId)).thenReturn(Optional.of(stock));
        when(inventoryStockRepository.save(any(InventoryStock.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryStockRepository.findLowStockByTenantId(tenantId)).thenReturn(List.of(stock));

        InventoryStockUpdateRequest updateReq = InventoryStockUpdateRequest.builder()
                .minimumStock(new BigDecimal("8.000"))
                .location("Cámara Fría 2")
                .build();

        InventoryStockResponse updated = inventoryStockService.update(tenantId, stockId, updateReq);
        assertEquals(new BigDecimal("4.000"), updated.quantity());
        assertEquals(new BigDecimal("8.000"), updated.minimumStock());
        assertEquals("Cámara Fría 2", updated.location());

        List<InventoryStockResponse> alerts = inventoryStockService.findLowStockAlerts(tenantId);
        assertEquals(1, alerts.size());
        assertTrue(alerts.get(0).lowStock());
    }
}
