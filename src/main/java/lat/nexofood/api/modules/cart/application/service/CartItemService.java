package lat.nexofood.api.modules.cart.application.service;

import lat.nexofood.api.modules.cart.domain.Cart;
import lat.nexofood.api.modules.cart.domain.CartItem;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemRequest;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemUpdateRequest;

import java.util.UUID;

public interface CartItemService {

    CartItem addItem(Cart cart, CartItemRequest request);

    CartItem updateItemQuantity(Cart cart, UUID itemId, int quantity);

    CartItem updateItemNotes(Cart cart, UUID itemId, String notes);

    CartItem updateItem(Cart cart, UUID itemId, CartItemUpdateRequest request);

    void removeItem(Cart cart, UUID itemId);
}
