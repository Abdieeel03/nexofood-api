package lat.nexofood.api.modules.inventory.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.inventory.domain.InventoryItem;
import lat.nexofood.api.modules.inventory.domain.InventoryStock;
import lat.nexofood.api.modules.inventory.infrastructure.repository.InventoryItemRepository;
import lat.nexofood.api.modules.inventory.infrastructure.repository.InventoryStockRepository;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryStockRequest;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryStockUpdateRequest;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryStockResponse;
import lat.nexofood.api.modules.inventory.web.mapper.InventoryStockMapper;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.infrastructure.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryStockServiceImpl implements InventoryStockService {

    private final InventoryStockRepository inventoryStockRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final TenantRepository tenantRepository;
    private final InventoryStockMapper inventoryStockMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryStockResponse> findByTenantPaginated(
            UUID tenantId,
            Boolean activeOnly,
            Pageable pageable
    ) {
        Page<InventoryStock> page = Boolean.TRUE.equals(activeOnly)
                ? inventoryStockRepository.findAllByTenantIdAndIsActiveTrue(tenantId, pageable)
                : inventoryStockRepository.findAllByTenantId(tenantId, pageable);
        return PageResponse.from(page.map(inventoryStockMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryStockResponse> findLowStockAlerts(UUID tenantId) {
        return inventoryStockRepository.findLowStockByTenantId(tenantId)
                .stream()
                .map(inventoryStockMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryStockResponse> findLowStockAlertsPaginated(UUID tenantId, Pageable pageable) {
        Page<InventoryStock> page = inventoryStockRepository.findLowStockByTenantId(tenantId, pageable);
        return PageResponse.from(page.map(inventoryStockMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryStockResponse findByIdAndTenant(UUID tenantId, UUID stockId) {
        InventoryStock stock = getStockOrThrow(tenantId, stockId);
        return inventoryStockMapper.toResponse(stock);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryStockResponse findByItemAndTenant(UUID tenantId, UUID itemId) {
        InventoryStock stock = inventoryStockRepository.findByTenantIdAndItemId(tenantId, itemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Registro de stock no encontrado para el insumo con ID: " + itemId
                ));
        return inventoryStockMapper.toResponse(stock);
    }

    @Override
    @Transactional
    public InventoryStockResponse create(UUID tenantId, InventoryStockRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado con ID: " + tenantId));

        InventoryItem item = inventoryItemRepository.findByIdAndTenantId(request.itemId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El insumo con ID " + request.itemId() + " no existe o no pertenece al restaurante."
                ));

        if (!Boolean.TRUE.equals(item.getIsActive())) {
            throw new BadRequestException("No se puede inicializar stock para un insumo inactivo.");
        }

        if (inventoryStockRepository.existsByTenantIdAndItemId(tenantId, request.itemId())) {
            throw new ResourceConflictException(
                    "Ya existe un registro de existencias (stock) para el insumo '" + item.getName() + "' en el restaurante."
            );
        }

        validateMinimumStock(request.minimumStock());

        InventoryStock stock = inventoryStockMapper.toEntity(request, tenant, item);
        InventoryStock saved = inventoryStockRepository.save(stock);
        return inventoryStockMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InventoryStockResponse update(UUID tenantId, UUID stockId, InventoryStockUpdateRequest request) {
        InventoryStock stock = getStockOrThrow(tenantId, stockId);

        BigDecimal minStock = request.minimumStock() != null
                ? request.minimumStock().setScale(3, RoundingMode.HALF_UP)
                : stock.getMinimumStock();

        validateMinimumStock(minStock);

        if (request.minimumStock() != null) {
            stock.setMinimumStock(minStock);
        }
        if (request.location() != null) {
            stock.setLocation(request.location().isBlank() ? null : request.location().trim());
        }
        if (request.isActive() != null) {
            stock.setIsActive(request.isActive());
        }

        InventoryStock updated = inventoryStockRepository.save(stock);
        return inventoryStockMapper.toResponse(updated);
    }

    private void validateMinimumStock(BigDecimal minimumStock) {
        if (minimumStock != null && minimumStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("El stock mínimo no puede ser negativo.");
        }
    }

    private InventoryStock getStockOrThrow(UUID tenantId, UUID stockId) {
        return inventoryStockRepository.findByIdAndTenantId(stockId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Registro de stock no encontrado con ID: " + stockId));
    }
}
