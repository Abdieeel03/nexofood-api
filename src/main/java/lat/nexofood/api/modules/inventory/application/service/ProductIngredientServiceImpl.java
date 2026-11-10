package lat.nexofood.api.modules.inventory.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lat.nexofood.api.modules.inventory.domain.InventoryItem;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementReason;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementType;
import lat.nexofood.api.modules.inventory.domain.InventoryStock;
import lat.nexofood.api.modules.inventory.domain.ProductIngredient;
import lat.nexofood.api.modules.inventory.infrastructure.repository.InventoryItemRepository;
import lat.nexofood.api.modules.inventory.infrastructure.repository.InventoryMovementRepository;
import lat.nexofood.api.modules.inventory.infrastructure.repository.InventoryStockRepository;
import lat.nexofood.api.modules.inventory.infrastructure.repository.ProductIngredientRepository;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryMovementRequest;
import lat.nexofood.api.modules.inventory.web.dto.request.ProductIngredientRequest;
import lat.nexofood.api.modules.inventory.web.dto.response.ProductIngredientResponse;
import lat.nexofood.api.modules.inventory.web.mapper.ProductIngredientMapper;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductIngredientServiceImpl implements ProductIngredientService {

    private final ProductIngredientRepository productIngredientRepository;
    private final ProductRepository productRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryStockRepository inventoryStockRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final InventoryMovementService inventoryMovementService;
    private final ProductIngredientMapper productIngredientMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ProductIngredientResponse> getRecipeByProduct(UUID tenantId, UUID productId) {
        getProductOrThrow(tenantId, productId);
        return productIngredientRepository.findAllByTenantIdAndProductId(tenantId, productId)
                .stream()
                .map(productIngredientMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProductIngredientResponse addOrUpdateIngredient(
            UUID tenantId,
            UUID productId,
            ProductIngredientRequest request
    ) {
        if (request == null || request.inventoryItemId() == null || request.quantity() == null) {
            throw new BadRequestException("El insumo y la cantidad son obligatorios para la receta.");
        }
        if (request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("La cantidad del ingrediente debe ser mayor a cero.");
        }

        Product product = getProductOrThrow(tenantId, productId);
        InventoryItem item = inventoryItemRepository.findByIdAndTenantId(request.inventoryItemId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El insumo con ID " + request.inventoryItemId() + " no existe o no pertenece al restaurante."
                ));

        if (!Boolean.TRUE.equals(item.getIsActive())) {
            throw new BadRequestException("No se puede asignar un insumo inactivo a una receta.");
        }

        BigDecimal scaledQuantity = request.quantity().setScale(3, RoundingMode.HALF_UP);

        Optional<ProductIngredient> existing = productIngredientRepository
                .findByTenantIdAndProductIdAndInventoryItemId(tenantId, productId, item.getId());

        ProductIngredient ingredient;
        if (existing.isPresent()) {
            ingredient = existing.get();
            ingredient.setQuantity(scaledQuantity);
        } else {
            ingredient = ProductIngredient.builder()
                    .tenant(product.getTenant())
                    .product(product)
                    .inventoryItem(item)
                    .quantity(scaledQuantity)
                    .build();
        }

        ProductIngredient saved = productIngredientRepository.save(ingredient);
        return productIngredientMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void removeIngredient(UUID tenantId, UUID productId, UUID ingredientId) {
        getProductOrThrow(tenantId, productId);
        ProductIngredient ingredient = productIngredientRepository
                .findByIdAndTenantIdAndProductId(ingredientId, tenantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ingrediente de receta no encontrado con ID: " + ingredientId
                ));
        productIngredientRepository.delete(ingredient);
    }

    @Override
    @Transactional
    public void consumeStockForOrder(Order order) {
        if (order == null || order.getTenant() == null || order.getItems() == null || order.getItems().isEmpty()) {
            return;
        }
        UUID tenantId = order.getTenant().getId();
        String orderRef = order.getOrderNumber() != null ? order.getOrderNumber() : (order.getId() != null ? order.getId().toString() : null);

        if (orderRef != null && inventoryMovementRepository.existsByTenantIdAndReasonDetailsStartingWithAndMovementType(
                tenantId, "Descuento automático por orden " + orderRef, InventoryMovementType.EXIT
        )) {
            return;
        }

        for (OrderItem orderItem : order.getItems()) {
            if (orderItem.getProduct() == null || orderItem.getProduct().getId() == null) {
                continue;
            }
            UUID productId = orderItem.getProduct().getId();
            List<ProductIngredient> recipe = productIngredientRepository.findAllByTenantIdAndProductId(tenantId, productId);
            if (recipe.isEmpty()) {
                continue;
            }

            BigDecimal itemQty = BigDecimal.valueOf(orderItem.getQuantity() != null ? orderItem.getQuantity() : 1);
            for (ProductIngredient ingredient : recipe) {
                InventoryItem invItem = ingredient.getInventoryItem();
                InventoryStock stock = inventoryStockRepository
                        .findByTenantIdAndItemId(tenantId, invItem.getId())
                        .orElseThrow(() -> new BadRequestException(
                                "El insumo '" + invItem.getName() + "' requerido por la receta de '" +
                                orderItem.getProductName() + "' no tiene un registro de stock configurado."
                        ));

                BigDecimal requiredQty = ingredient.getQuantity()
                        .multiply(itemQty)
                        .setScale(3, RoundingMode.HALF_UP);

                inventoryMovementService.register(
                        tenantId,
                        InventoryMovementRequest.builder()
                                .stockId(stock.getId())
                                .movementType(InventoryMovementType.EXIT)
                                .reason(InventoryMovementReason.SALE)
                                .quantity(requiredQty)
                                .reasonDetails("Descuento automático por orden " + (orderRef != null ? orderRef : "")
                                        + " (" + orderItem.getProductName() + " x" + orderItem.getQuantity() + ")")
                                .build()
                );
            }
        }
    }

    @Override
    @Transactional
    public void restoreStockForCanceledOrder(Order order) {
        if (order == null || order.getTenant() == null || order.getItems() == null || order.getItems().isEmpty()) {
            return;
        }
        UUID tenantId = order.getTenant().getId();
        String orderRef = order.getOrderNumber() != null ? order.getOrderNumber() : (order.getId() != null ? order.getId().toString() : null);

        if (orderRef != null && inventoryMovementRepository.existsByTenantIdAndReasonDetailsStartingWithAndMovementType(
                tenantId, "Reverso automático por cancelación de orden " + orderRef, InventoryMovementType.ENTRY
        )) {
            return;
        }

        for (OrderItem orderItem : order.getItems()) {
            if (orderItem.getProduct() == null || orderItem.getProduct().getId() == null) {
                continue;
            }
            UUID productId = orderItem.getProduct().getId();
            List<ProductIngredient> recipe = productIngredientRepository.findAllByTenantIdAndProductId(tenantId, productId);
            if (recipe.isEmpty()) {
                continue;
            }

            BigDecimal itemQty = BigDecimal.valueOf(orderItem.getQuantity() != null ? orderItem.getQuantity() : 1);
            for (ProductIngredient ingredient : recipe) {
                InventoryItem invItem = ingredient.getInventoryItem();
                Optional<InventoryStock> stockOpt = inventoryStockRepository.findByTenantIdAndItemId(tenantId, invItem.getId());
                if (stockOpt.isEmpty()) {
                    continue;
                }

                BigDecimal restoredQty = ingredient.getQuantity()
                        .multiply(itemQty)
                        .setScale(3, RoundingMode.HALF_UP);

                inventoryMovementService.register(
                        tenantId,
                        InventoryMovementRequest.builder()
                                .stockId(stockOpt.get().getId())
                                .movementType(InventoryMovementType.ENTRY)
                                .reason(InventoryMovementReason.CUSTOMER_RETURN)
                                .quantity(restoredQty)
                                .reasonDetails("Reverso automático por cancelación de orden " + (orderRef != null ? orderRef : "")
                                        + " (" + orderItem.getProductName() + " x" + orderItem.getQuantity() + ")")
                                .build()
                );
            }
        }
    }

    private Product getProductOrThrow(UUID tenantId, UUID productId) {
        return productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + productId));
    }
}
