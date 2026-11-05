package lat.nexofood.api.modules.inventory.application.service;

import jakarta.persistence.criteria.Predicate;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.inventory.domain.InventoryItem;
import lat.nexofood.api.modules.inventory.domain.InventoryMovement;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementReason;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementType;
import lat.nexofood.api.modules.inventory.domain.InventoryStock;
import lat.nexofood.api.modules.inventory.infrastructure.repository.InventoryMovementRepository;
import lat.nexofood.api.modules.inventory.infrastructure.repository.InventoryStockRepository;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryMovementRequest;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryMovementResponse;
import lat.nexofood.api.modules.inventory.web.mapper.InventoryMovementMapper;
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
public class InventoryMovementServiceImpl implements InventoryMovementService {

    private final InventoryMovementRepository inventoryMovementRepository;
    private final InventoryStockRepository inventoryStockRepository;
    private final CurrentUserProvider currentUserProvider;
    private final InventoryMovementMapper inventoryMovementMapper;

    @Override
    @Transactional
    public InventoryMovementResponse register(UUID tenantId, InventoryMovementRequest request) {
        if (request == null) {
            throw new BadRequestException("Los datos del movimiento de inventario son obligatorios.");
        }
        if (request.movementType() == null || request.reason() == null) {
            throw new BadRequestException("El tipo de movimiento y el motivo son obligatorios.");
        }
        if (!request.reason().isCompatibleWith(request.movementType())) {
            throw new BadRequestException(String.format(
                    "El motivo '%s' no es coherente con el tipo de movimiento '%s'. Se esperaba un movimiento de tipo '%s'.",
                    request.reason(),
                    request.movementType(),
                    request.reason().getExpectedMovementType()
            ));
        }
        if (request.reasonDetails() == null || request.reasonDetails().isBlank()) {
            throw new BadRequestException("La justificación del movimiento (reasonDetails) es obligatoria.");
        }
        if (request.quantity() == null) {
            throw new BadRequestException("La cantidad del movimiento es obligatoria.");
        }

        BigDecimal movementQty = request.quantity().setScale(3, RoundingMode.HALF_UP);
        if (request.movementType() == InventoryMovementType.ENTRY
                || request.movementType() == InventoryMovementType.EXIT) {
            if (movementQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Para movimientos ENTRY o EXIT, la cantidad debe ser mayor a cero.");
            }
        } else if (request.movementType() == InventoryMovementType.ADJUSTMENT) {
            if (movementQty.compareTo(BigDecimal.ZERO) == 0) {
                throw new BadRequestException("Para un movimiento ADJUSTMENT, la cantidad no puede ser cero.");
            }
        }

        // Bloqueo pesimista (SELECT ... FOR UPDATE) para evitar condiciones de carrera sobre el stock
        InventoryStock stock = inventoryStockRepository.findByIdAndTenantIdForUpdate(request.stockId(), tenantId)
                .orElseGet(() -> inventoryStockRepository.findByIdAndTenantId(request.stockId(), tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Registro de stock no encontrado con ID: " + request.stockId()
                        )));

        if (!Boolean.TRUE.equals(stock.getIsActive())) {
            throw new BadRequestException("No se pueden registrar movimientos sobre un registro de stock inactivo.");
        }

        InventoryItem item = stock.getItem();
        if (item != null && !Boolean.TRUE.equals(item.getIsActive())) {
            throw new BadRequestException("No se pueden registrar movimientos para un insumo inactivo.");
        }

        BigDecimal previousQuantity = (stock.getQuantity() != null ? stock.getQuantity() : BigDecimal.ZERO)
                .setScale(3, RoundingMode.HALF_UP);
        BigDecimal newQuantity;

        switch (request.movementType()) {
            case ENTRY -> newQuantity = previousQuantity.add(movementQty);
            case EXIT -> newQuantity = previousQuantity.subtract(movementQty);
            case ADJUSTMENT -> newQuantity = previousQuantity.add(movementQty);
            default -> throw new BadRequestException("Tipo de movimiento no soportado: " + request.movementType());
        }

        newQuantity = newQuantity.setScale(3, RoundingMode.HALF_UP);
        if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException(String.format(
                    "Stock insuficiente para '%s'. Saldo actual: %s, cantidad solicitada: %s, saldo resultante: %s (no puede ser negativo).",
                    item != null ? item.getName() : stock.getId(),
                    previousQuantity,
                    movementQty,
                    newQuantity
            ));
        }

        User operator = resolveCurrentUser(stock);

        stock.setQuantity(newQuantity);
        inventoryStockRepository.save(stock);

        InventoryMovement movement = InventoryMovement.builder()
                .tenant(stock.getTenant())
                .stock(stock)
                .item(item)
                .movementType(request.movementType())
                .reason(request.reason())
                .quantity(movementQty)
                .previousQuantity(previousQuantity)
                .newQuantity(newQuantity)
                .performedBy(operator)
                .performedByName(operator != null ? operator.getFullName() : null)
                .reasonDetails(request.reasonDetails().trim())
                .referenceId(request.referenceId())
                .referenceType(request.referenceType() != null && !request.referenceType().isBlank()
                        ? request.referenceType().trim()
                        : null)
                .build();

        InventoryMovement savedMovement = inventoryMovementRepository.save(movement);
        return inventoryMovementMapper.toResponse(savedMovement);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryMovementResponse> getKardexByStock(
            UUID tenantId,
            UUID stockId,
            Pageable pageable
    ) {
        inventoryStockRepository.findByIdAndTenantId(stockId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Registro de stock no encontrado con ID: " + stockId));

        Page<InventoryMovement> page = inventoryMovementRepository
                .findAllByTenantIdAndStockIdOrderByCreatedAtDesc(tenantId, stockId, pageable);
        return PageResponse.from(page.map(inventoryMovementMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryMovementResponse> findByTenantPaginated(
            UUID tenantId,
            InventoryMovementType movementType,
            InventoryMovementReason reason,
            Pageable pageable
    ) {
        Specification<InventoryMovement> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenant").get("id"), tenantId));
            if (movementType != null) {
                predicates.add(cb.equal(root.get("movementType"), movementType));
            }
            if (reason != null) {
                predicates.add(cb.equal(root.get("reason"), reason));
            }
            if (query != null && (query.getOrderList() == null || query.getOrderList().isEmpty())) {
                query.orderBy(cb.desc(root.get("createdAt")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<InventoryMovement> page = inventoryMovementRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(inventoryMovementMapper::toResponse));
    }

    private User resolveCurrentUser(InventoryStock stock) {
        if (currentUserProvider != null) {
            try {
                return currentUserProvider.getCurrentUser();
            } catch (Exception e) {
                if (stock != null && stock.getTenant() != null && stock.getTenant().getOwner() != null) {
                    return stock.getTenant().getOwner();
                }
                throw e;
            }
        }
        return stock != null && stock.getTenant() != null ? stock.getTenant().getOwner() : null;
    }
}
