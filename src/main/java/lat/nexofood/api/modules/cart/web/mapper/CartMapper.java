package lat.nexofood.api.modules.cart.web.mapper;

import lat.nexofood.api.modules.cart.domain.Cart;
import lat.nexofood.api.modules.cart.web.dto.response.CartItemResponse;
import lat.nexofood.api.modules.cart.web.dto.response.CartResponse;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CartMapper {

    private final CartItemMapper cartItemMapper;

    public CartResponse toResponse(Cart cart) {
        if (cart == null) {
            return null;
        }

        List<CartItemResponse> itemResponses = cart.getItems() != null
                ? cart.getItems().stream().map(cartItemMapper::toResponse).toList()
                : Collections.emptyList();

        TenantCustomer customer = cart.getCustomer();
        UUID customerUserId = null;
        if (customer != null) {
            if (customer.getUser() != null && customer.getUser().getId() != null) {
                customerUserId = customer.getUser().getId();
            } else if (customer.getId() != null) {
                customerUserId = customer.getId().getUserId();
            }
        }

        return CartResponse.builder()
                .id(cart.getId())
                .tenantId(cart.getTenant() != null ? cart.getTenant().getId() : null)
                .customerId(customerUserId)
                .customerUserId(customerUserId)
                .total(cart.getTotal())
                .notes(cart.getNotes())
                .items(itemResponses)
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }
}
