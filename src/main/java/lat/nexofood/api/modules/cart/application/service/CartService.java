package lat.nexofood.api.modules.cart.application.service;

import lat.nexofood.api.modules.cart.domain.Cart;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemRequest;
import lat.nexofood.api.modules.cart.web.dto.request.CartItemUpdateRequest;
import lat.nexofood.api.modules.cart.web.dto.request.CartNotesRequest;
import lat.nexofood.api.modules.cart.web.dto.response.CartResponse;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;

import java.util.UUID;

public interface CartService {

    Cart getOrCreateCart(Tenant tenant, TenantCustomer customer);

    CartResponse getMyCart(String slug);

    CartResponse addItem(String slug, CartItemRequest request);

    CartResponse updateItem(String slug, UUID itemId, CartItemUpdateRequest request);

    CartResponse removeItem(String slug, UUID itemId);

    CartResponse updateCartNotes(String slug, CartNotesRequest request);

    CartResponse clearCart(String slug);
}
