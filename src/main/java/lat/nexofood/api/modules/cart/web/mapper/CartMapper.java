package lat.nexofood.api.modules.cart.web.mapper;

import lat.nexofood.api.modules.cart.domain.Cart;
import lat.nexofood.api.modules.cart.web.dto.response.CartItemResponse;
import lat.nexofood.api.modules.cart.web.dto.response.CartResponse;
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

        UUID customerId = cart.getCustomer() != null ? cart.getCustomer().getId() : null;
        UUID customerUserId = (cart.getCustomer() != null && cart.getCustomer().getUser() != null)
                ? cart.getCustomer().getUser().getId()
                : null;

        return CartResponse.builder()
                .id(cart.getId())
                .tenantId(cart.getTenant() != null ? cart.getTenant().getId() : null)
                .customerId(customerId)
                .customerUserId(customerUserId)
                .total(cart.getTotal())
                .notes(cart.getNotes())
                .items(itemResponses)
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }
}
