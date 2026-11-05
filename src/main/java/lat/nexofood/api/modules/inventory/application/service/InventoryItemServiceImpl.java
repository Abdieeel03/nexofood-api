package lat.nexofood.api.modules.inventory.application.service;

import jakarta.persistence.criteria.Predicate;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.response.PageResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryItemServiceImpl implements InventoryItemService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryStockRepository inventoryStockRepository;
    private final ProductIngredientRepository productIngredientRepository;
    private final TenantRepository tenantRepository;
    private final InventoryItemMapper inventoryItemMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemResponse> findByTenantPaginated(
            UUID tenantId,
            Boolean activeOnly,
            String category,
            InventoryUnit unit,
            Pageable pageable
    ) {
        Specification<InventoryItem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenant").get("id"), tenantId));
            if (Boolean.TRUE.equals(activeOnly)) {
                predicates.add(cb.isTrue(root.get("isActive")));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase()));
            }
            if (unit != null) {
                predicates.add(cb.equal(root.get("unit"), unit));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<InventoryItem> page = inventoryItemRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(inventoryItemMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemResponse> findAllByTenant(UUID tenantId, boolean activeOnly) {
        List<InventoryItem> items = activeOnly
                ? inventoryItemRepository.findAllByTenantIdAndIsActiveTrue(tenantId)
                : inventoryItemRepository.findAllByTenantId(tenantId);
        return items.stream()
                .map(inventoryItemMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryItemResponse findByIdAndTenant(UUID tenantId, UUID itemId) {
        InventoryItem item = getItemOrThrow(tenantId, itemId);
        return inventoryItemMapper.toResponse(item);
    }

    @Override
    @Transactional
    public InventoryItemResponse create(UUID tenantId, InventoryItemRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado con ID: " + tenantId));

        String normalizedName = request.name() != null ? request.name().trim() : "";
        if (normalizedName.isEmpty()) {
            throw new BadRequestException("El nombre del insumo es obligatorio.");
        }
        if (inventoryItemRepository.existsByTenantIdAndNameIgnoreCase(tenantId, normalizedName)) {
            throw new ResourceConflictException("Ya existe un insumo con el nombre '" + normalizedName + "' en el restaurante.");
        }

        String normalizedSku = normalizeOptional(request.sku());
        if (normalizedSku != null && inventoryItemRepository.existsByTenantIdAndSkuIgnoreCase(tenantId, normalizedSku)) {
            throw new ResourceConflictException("Ya existe un insumo con el SKU '" + normalizedSku + "' en el restaurante.");
        }

        if (request.costPrice() != null && request.costPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("El costo unitario del insumo no puede ser negativo.");
        }

        InventoryItem item = inventoryItemMapper.toEntity(request, tenant);
        item.setName(normalizedName);
        item.setSku(normalizedSku);

        InventoryItem saved = inventoryItemRepository.save(item);
        return inventoryItemMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InventoryItemResponse update(UUID tenantId, UUID itemId, InventoryItemRequest request) {
        InventoryItem item = getItemOrThrow(tenantId, itemId);

        String normalizedName = request.name() != null ? request.name().trim() : "";
        if (normalizedName.isEmpty()) {
            throw new BadRequestException("El nombre del insumo es obligatorio.");
        }
        if (inventoryItemRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, normalizedName, itemId)) {
            throw new ResourceConflictException("Ya existe otro insumo con el nombre '" + normalizedName + "' en el restaurante.");
        }

        String normalizedSku = normalizeOptional(request.sku());
        if (normalizedSku != null && inventoryItemRepository.existsByTenantIdAndSkuIgnoreCaseAndIdNot(tenantId, normalizedSku, itemId)) {
            throw new ResourceConflictException("Ya existe otro insumo con el SKU '" + normalizedSku + "' en el restaurante.");
        }

        if (request.costPrice() != null && request.costPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("El costo unitario del insumo no puede ser negativo.");
        }

        item.setName(normalizedName);
        item.setSku(normalizedSku);
        item.setDescription(request.description());
        item.setCategory(normalizeOptional(request.category()));
        if (request.unit() != null) {
            item.setUnit(request.unit());
        }
        if (request.costPrice() != null) {
            item.setCostPrice(request.costPrice().setScale(2, RoundingMode.HALF_UP));
        }
        if (request.isActive() != null) {
            item.setIsActive(request.isActive());
        }

        InventoryItem updated = inventoryItemRepository.save(item);
        return inventoryItemMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public InventoryItemResponse toggleStatus(UUID tenantId, UUID itemId, boolean isActive) {
        InventoryItem item = getItemOrThrow(tenantId, itemId);
        item.setIsActive(isActive);
        InventoryItem updated = inventoryItemRepository.save(item);
        return inventoryItemMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void delete(UUID tenantId, UUID itemId) {
        InventoryItem item = getItemOrThrow(tenantId, itemId);

        if (inventoryStockRepository.existsByTenantIdAndItemId(tenantId, itemId)) {
            throw new BadRequestException(
                    "No se puede eliminar un insumo que ya tiene un registro de existencias (stock). Desactívelo en su lugar."
            );
        }
        if (productIngredientRepository != null
                && productIngredientRepository.existsByTenantIdAndInventoryItemId(tenantId, itemId)) {
            throw new BadRequestException(
                    "No se puede eliminar un insumo asociado a la receta de un producto. Retire el ingrediente de la receta o desactívelo."
            );
        }

        inventoryItemRepository.delete(item);
    }

    private InventoryItem getItemOrThrow(UUID tenantId, UUID itemId) {
        return inventoryItemRepository.findByIdAndTenantId(itemId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo de inventario no encontrado con ID: " + itemId));
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
