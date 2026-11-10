package lat.nexofood.api.modules.cart.application.service;

import lat.nexofood.api.modules.cart.domain.Cart;
import lat.nexofood.api.modules.cart.domain.CartItem;
import lat.nexofood.api.modules.cart.infrastructure.repository.CartItemRepository;
import lat.nexofood.api.modules.cart.infrastructure.repository.CartRepository;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemRequest;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemUpdateRequest;
import lat.nexofood.api.modules.cart.web.dto.request.CartNotesRequest;
import lat.nexofood.api.modules.cart.web.dto.response.CartItemResponse;
import lat.nexofood.api.modules.cart.web.dto.response.CartResponse;
import lat.nexofood.api.modules.cart.web.mapper.CartItemMapper;
import lat.nexofood.api.modules.cart.web.mapper.CartMapper;
import lat.nexofood.api.modules.catalog.application.service.ProductPricingService;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.store.application.service.customer.CurrentCustomerResolver;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartItemService cartItemService;
    private final CurrentCustomerResolver currentCustomerResolver;
    private final ProductPricingService productPricingService;
    private final CartMapper cartMapper;
    private final CartItemMapper cartItemMapper;

    @Override
    @Transactional
    public Cart getOrCreateCart(Tenant tenant, TenantCustomer customer) {
        UUID userId = customer.getUser() != null && customer.getUser().getId() != null
                ? customer.getUser().getId()
                : customer.getId().getUserId();

        return cartRepository.findByTenantIdAndCustomerUserId(tenant.getId(), userId)
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .tenant(tenant)
                            .customer(customer)
                            .total(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                            .items(new ArrayList<>())
                            .build();
                    return cartRepository.save(newCart);
                });
    }

    @Override
    @Transactional
    public CartResponse getMyCart(String slug) {
        TenantCustomer customer = currentCustomerResolver.resolveBySlug(slug);
        Cart cart = getOrCreateCart(customer.getTenant(), customer);
        return reconcileAndMap(cart);
    }

    @Override
    @Transactional
    public CartResponse addItem(String slug, CartItemRequest request) {
        TenantCustomer customer = currentCustomerResolver.resolveBySlug(slug);
        Cart cart = getOrCreateCart(customer.getTenant(), customer);
        cartItemService.addItem(cart, request);
        cartRepository.save(cart);
        return reconcileAndMap(cart);
    }

    @Override
    @Transactional
    public CartResponse updateItem(String slug, UUID itemId, CartItemUpdateRequest request) {
        TenantCustomer customer = currentCustomerResolver.resolveBySlug(slug);
        Cart cart = getOrCreateCart(customer.getTenant(), customer);
        cartItemService.updateItem(cart, itemId, request);
        cartRepository.save(cart);
        return reconcileAndMap(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(String slug, UUID itemId) {
        TenantCustomer customer = currentCustomerResolver.resolveBySlug(slug);
        Cart cart = getOrCreateCart(customer.getTenant(), customer);
        cartItemService.removeItem(cart, itemId);
        cartRepository.save(cart);
        return reconcileAndMap(cart);
    }

    @Override
    @Transactional
    public CartResponse updateCartNotes(String slug, CartNotesRequest request) {
        TenantCustomer customer = currentCustomerResolver.resolveBySlug(slug);
        Cart cart = getOrCreateCart(customer.getTenant(), customer);
        cart.setNotes(request != null ? request.notes() : null);
        cartRepository.save(cart);
        return reconcileAndMap(cart);
    }

    @Override
    @Transactional
    public CartResponse clearCart(String slug) {
        TenantCustomer customer = currentCustomerResolver.resolveBySlug(slug);
        Cart cart = getOrCreateCart(customer.getTenant(), customer);
        if (cart.getId() != null) {
            cartItemRepository.deleteAllByCartId(cart.getId());
        }
        cart.clear();
        cartRepository.save(cart);
        return cartMapper.toResponse(cart);
    }

    private CartResponse reconcileAndMap(Cart cart) {
        List<CartItemResponse> itemResponses = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean hasPriceChanges = false;
        boolean hasUnavailableItems = false;

        if (cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                Product product = item.getProduct();
                boolean available = isProductAvailable(product);
                BigDecimal previousPrice = null;
                boolean priceChanged = false;

                if (product != null) {
                    BigDecimal livePrice = productPricingService.calculateCurrentPrice(product)
                            .setScale(2, RoundingMode.HALF_UP);
                    if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(livePrice) != 0) {
                        previousPrice = item.getUnitPrice();
                        item.repriceTo(livePrice);
                        cartItemRepository.save(item);
                        priceChanged = true;
                        hasPriceChanges = true;
                        warnings.add(String.format(
                                "El precio de '%s' cambió de %s a %s",
                                product.getName(),
                                previousPrice,
                                livePrice
                        ));
                    }
                    if (!available) {
                        hasUnavailableItems = true;
                        warnings.add(String.format(
                                "El producto '%s' ya no se encuentra disponible en la tienda",
                                product.getName()
                        ));
                    }
                } else {
                    hasUnavailableItems = true;
                }

                itemResponses.add(cartItemMapper.toResponse(item, previousPrice, priceChanged, available));
            }
        }

        cart.calculateTotal();
        return cartMapper.toResponse(cart, itemResponses, hasPriceChanges, hasUnavailableItems, warnings);
    }

    private boolean isProductAvailable(Product product) {
        if (product == null) {
            return false;
        }
        boolean productActive = Boolean.TRUE.equals(product.getIsAvailable());
        boolean categoryActive = product.getCategory() == null
                || Boolean.TRUE.equals(product.getCategory().getIsActive());
        return productActive && categoryActive;
    }
}
