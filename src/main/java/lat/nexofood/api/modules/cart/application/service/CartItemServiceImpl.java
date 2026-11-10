package lat.nexofood.api.modules.cart.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.exception.ResourceNotFoundException;
import lat.nexofood.api.modules.cart.domain.Cart;
import lat.nexofood.api.modules.cart.domain.CartItem;
import lat.nexofood.api.modules.cart.infrastructure.repository.CartItemRepository;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemRequest;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemUpdateRequest;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingService;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.catalog.infrastructure.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartItemServiceImpl implements CartItemService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductPricingService productPricingService;

    @Override
    @Transactional
    public CartItem addItem(Cart cart, CartItemRequest request) {
        if (cart == null || cart.getTenant() == null) {
            throw new BadRequestException("El carrito y la tienda asociada son obligatorios");
        }
        if (request == null || request.productId() == null) {
            throw new BadRequestException("El ID del producto es obligatorio");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new BadRequestException("La cantidad del ítem debe ser mayor a 0");
        }

        UUID tenantId = cart.getTenant().getId();
        Product product = productRepository.findByIdAndTenantId(request.productId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El producto solicitado no existe o no pertenece a esta tienda"
                ));

        validateProductAvailable(product);

        BigDecimal currentUnitPrice = productPricingService.calculateCurrentPrice(product);
        CartItem item = cart.addItem(product, request.quantity(), currentUnitPrice, request.notes());
        CartItem savedItem = cartItemRepository.save(item);
        cart.calculateTotal();
        return savedItem;
    }

    @Override
    @Transactional
    public CartItem updateItemQuantity(Cart cart, UUID itemId, int quantity) {
        CartItem item = findItemInCartOrThrow(cart, itemId);
        validateProductAvailable(item.getProduct());

        BigDecimal currentUnitPrice = productPricingService.calculateCurrentPrice(item.getProduct());
        item.repriceTo(currentUnitPrice);
        item.updateQuantity(quantity);

        CartItem savedItem = cartItemRepository.save(item);
        cart.calculateTotal();
        return savedItem;
    }

    @Override
    @Transactional
    public CartItem updateItemNotes(Cart cart, UUID itemId, String notes) {
        CartItem item = findItemInCartOrThrow(cart, itemId);
        item.setNotes(notes);
        return cartItemRepository.save(item);
    }

    @Override
    @Transactional
    public CartItem updateItem(Cart cart, UUID itemId, CartItemUpdateRequest request) {
        if (request == null || (request.quantity() == null && request.notes() == null)) {
            throw new BadRequestException("Debe especificar la cantidad o las notas del ítem a actualizar");
        }

        CartItem item = findItemInCartOrThrow(cart, itemId);

        if (request.quantity() != null) {
            validateProductAvailable(item.getProduct());
            BigDecimal currentUnitPrice = productPricingService.calculateCurrentPrice(item.getProduct());
            item.repriceTo(currentUnitPrice);
            item.updateQuantity(request.quantity());
        }

        if (request.notes() != null) {
            item.setNotes(request.notes());
        }

        CartItem savedItem = cartItemRepository.save(item);
        cart.calculateTotal();
        return savedItem;
    }

    @Override
    @Transactional
    public void removeItem(Cart cart, UUID itemId) {
        CartItem item = findItemInCartOrThrow(cart, itemId);
        cart.removeItem(item.getId());
        cartItemRepository.delete(item);
        cart.calculateTotal();
    }

    private CartItem findItemInCartOrThrow(Cart cart, UUID itemId) {
        if (cart == null || itemId == null) {
            throw new ResourceNotFoundException("Ítem no encontrado en tu carrito");
        }

        if (cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                if (itemId.equals(item.getId())) {
                    return item;
                }
            }
        }

        if (cart.getId() != null) {
            return cartItemRepository.findByIdAndCartId(itemId, cart.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ítem no encontrado en tu carrito"));
        }

        throw new ResourceNotFoundException("Ítem no encontrado en tu carrito");
    }

    private void validateProductAvailable(Product product) {
        if (product == null || !Boolean.TRUE.equals(product.getIsAvailable())) {
            String productName = product != null ? product.getName() : "solicitado";
            throw new BadRequestException("El producto '" + productName + "' no se encuentra disponible actualmente");
        }
        if (product.getCategory() != null && !Boolean.TRUE.equals(product.getCategory().getIsActive())) {
            throw new BadRequestException("El producto '" + product.getName() + "' pertenece a una categoría inactiva");
        }
    }
}
