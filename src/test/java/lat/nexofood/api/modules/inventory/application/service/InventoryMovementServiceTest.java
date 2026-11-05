package lat.nexofood.api.modules.inventory.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.inventory.domain.InventoryItem;
import lat.nexofood.api.modules.inventory.domain.InventoryMovement;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementReason;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementType;
import lat.nexofood.api.modules.inventory.domain.InventoryStock;
import lat.nexofood.api.modules.inventory.domain.InventoryUnit;
import lat.nexofood.api.modules.inventory.infrastructure.repository.InventoryMovementRepository;
import lat.nexofood.api.modules.inventory.infrastructure.repository.InventoryStockRepository;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryMovementRequest;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryMovementResponse;
import lat.nexofood.api.modules.inventory.web.mapper.InventoryMovementMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryMovementServiceTest {

    @Mock
    private InventoryMovementRepository inventoryMovementRepository;
    @Mock
    private InventoryStockRepository inventoryStockRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private InventoryMovementServiceImpl inventoryMovementService;
    private UUID tenantId;
    private UUID stockId;
    private Tenant tenant;
    private User operator;
    private InventoryItem item;
    private InventoryStock stock;

    @BeforeEach
    void setUp() {
        inventoryMovementService = new InventoryMovementServiceImpl(
                inventoryMovementRepository,
                inventoryStockRepository,
                currentUserProvider,
                new InventoryMovementMapper()
        );
        tenantId = UUID.randomUUID();
        stockId = UUID.randomUUID();
        operator = User.builder()
                .id(UUID.randomUUID())
                .fullName("Mario Rossi")
                .email("mario@test.nexofood.lat")
                .build();
        tenant = Tenant.builder()
                .id(tenantId)
                .name("Pizzería Bella Napoli")
                .owner(operator)
                .build();
        item = InventoryItem.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .name("Harina de Trigo 00")
                .sku("INS-HAR-01")
                .unit(InventoryUnit.KG)
                .isActive(true)
                .build();
        stock = InventoryStock.builder()
                .id(stockId)
                .tenant(tenant)
                .item(item)
                .quantity(new BigDecimal("50.000"))
                .reservedQuantity(new BigDecimal("0.000"))
                .minimumStock(new BigDecimal("10.000"))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("register ENTRY: incrementa stock con bloqueo pesimista y guarda snapshot del operador")
    void registerEntryMovementUpdatesStock() {
        InventoryMovementRequest request = InventoryMovementRequest.builder()
                .stockId(stockId)
                .movementType(InventoryMovementType.ENTRY)
                .reason(InventoryMovementReason.PURCHASE)
                .quantity(new BigDecimal("25.500"))
                .reasonDetails("Factura F001-00982 proveedor Molitalia")
                .build();

        when(inventoryStockRepository.findByIdAndTenantIdForUpdate(stockId, tenantId)).thenReturn(Optional.of(stock));
        when(currentUserProvider.getCurrentUser()).thenReturn(operator);
        when(inventoryMovementRepository.save(any(InventoryMovement.class))).thenAnswer(inv -> {
            InventoryMovement m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        InventoryMovementResponse response = inventoryMovementService.register(tenantId, request);

        assertEquals(new BigDecimal("50.000"), response.previousQuantity());
        assertEquals(new BigDecimal("75.500"), response.newQuantity());
        assertEquals(new BigDecimal("75.500"), stock.getQuantity());
        assertEquals(operator.getId(), response.performedById());
        assertEquals("Mario Rossi", response.performedByName());
    }

    @Test
    @DisplayName("register EXIT: descuenta stock cuando hay saldo suficiente y bloquea si quedaría negativo")
    void registerExitMovementPreventsNegativeStock() {
        when(inventoryStockRepository.findByIdAndTenantIdForUpdate(stockId, tenantId)).thenReturn(Optional.of(stock));

        // 1. Intento de sacar más de lo disponible (60 > 50) -> lanza BadRequestException
        InventoryMovementRequest excessiveExit = InventoryMovementRequest.builder()
                .stockId(stockId)
                .movementType(InventoryMovementType.EXIT)
                .reason(InventoryMovementReason.KITCHEN_CONSUMPTION_OR_WASTE)
                .quantity(new BigDecimal("60.000"))
                .reasonDetails("Intento de salida mayor al saldo")
                .build();

        assertThrows(BadRequestException.class, () -> inventoryMovementService.register(tenantId, excessiveExit));
        assertEquals(new BigDecimal("50.000"), stock.getQuantity());
        verify(inventoryMovementRepository, never()).save(any());

        // 2. Salida válida (15 <= 50) -> nuevo saldo 35.000
        when(currentUserProvider.getCurrentUser()).thenReturn(operator);
        when(inventoryMovementRepository.save(any(InventoryMovement.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryMovementRequest validExit = InventoryMovementRequest.builder()
                .stockId(stockId)
                .movementType(InventoryMovementType.EXIT)
                .reason(InventoryMovementReason.KITCHEN_CONSUMPTION_OR_WASTE)
                .quantity(new BigDecimal("15.000"))
                .reasonDetails("Producción de masa matutina")
                .build();

        InventoryMovementResponse response = inventoryMovementService.register(tenantId, validExit);
        assertEquals(new BigDecimal("50.000"), response.previousQuantity());
        assertEquals(new BigDecimal("35.000"), response.newQuantity());
        assertEquals(new BigDecimal("35.000"), stock.getQuantity());
    }

    @Test
    @DisplayName("register: rechaza incoherencia entre movementType y reason (ej. PURCHASE con EXIT)")
    void registerRejectsIncompatibleTypeAndReason() {
        InventoryMovementRequest invalidRequest = InventoryMovementRequest.builder()
                .stockId(stockId)
                .movementType(InventoryMovementType.EXIT)
                .reason(InventoryMovementReason.PURCHASE)
                .quantity(new BigDecimal("10.000"))
                .reasonDetails("Incoherente")
                .build();

        assertThrows(BadRequestException.class, () -> inventoryMovementService.register(tenantId, invalidRequest));
        verify(inventoryStockRepository, never()).findByIdAndTenantIdForUpdate(any(), any());
    }

    @Test
    @DisplayName("register ADJUSTMENT: permite ajustes positivos o negativos siempre que el saldo final no sea negativo")
    void registerAdjustmentWorksForPositiveAndNegativeDeltas() {
        when(inventoryStockRepository.findByIdAndTenantIdForUpdate(stockId, tenantId)).thenReturn(Optional.of(stock));
        when(currentUserProvider.getCurrentUser()).thenReturn(operator);
        when(inventoryMovementRepository.save(any(InventoryMovement.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryMovementRequest negativeAdj = InventoryMovementRequest.builder()
                .stockId(stockId)
                .movementType(InventoryMovementType.ADJUSTMENT)
                .reason(InventoryMovementReason.PHYSICAL_COUNT)
                .quantity(new BigDecimal("-3.500"))
                .reasonDetails("Diferencia detectada en arqueo semanal")
                .build();

        InventoryMovementResponse response = inventoryMovementService.register(tenantId, negativeAdj);
        assertEquals(new BigDecimal("50.000"), response.previousQuantity());
        assertEquals(new BigDecimal("46.500"), response.newQuantity());
        assertEquals(new BigDecimal("46.500"), stock.getQuantity());
    }
}
