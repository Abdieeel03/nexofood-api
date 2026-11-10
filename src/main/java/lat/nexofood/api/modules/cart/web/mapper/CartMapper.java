package lat.nexofood.api.modules.cart.web.mapper;

import lat.nexofood.api.modules.cart.domain.Cart;
import lat.nexofood.api.modules.cart.web.dto.response.CartItemResponse;
import lat.nexofood.api.modules.cart.web.dto.response.CartResponse;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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

        boolean hasUnavailable = itemResponses.stream()
                .anyMatch(item -> Boolean.FALSE.equals(item.productAvailable()));

        return toResponse(cart, itemResponses, false, hasUnavailable, Collections.emptyList());
    }

    public CartResponse toResponse(
            Cart cart,
            List<CartItemResponse> itemResponses,
            boolean hasPriceChanges,
            boolean hasUnavailableItems,
            List<String> warnings
    ) {
        if (cart == null) {
            return null;
        }

        Tenant tenant = cart.getTenant();
        TenantCustomer customer = cart.getCustomer();
        UUID customerUserId = null;
        if (customer != null) {
            if (customer.getUser() != null && customer.getUser().getId() != null) {
                customerUserId = customer.getUser().getId();
            } else if (customer.getId() != null) {
                customerUserId = customer.getId().getUserId();
            }
        }

        BigDecimal total = cart.getTotal() != null ? cart.getTotal() : cart.calculateTotal();
        if (cart.getItems() != null && !cart.getItems().isEmpty()) {
            total = cart.calculateTotal();
        }

        return CartResponse.builder()
                .id(cart.getId())
                .tenantId(tenant != null ? tenant.getId() : null)
                .tenantSlug(tenant != null ? tenant.getSlug() : null)
                .tenantName(tenant != null ? tenant.getName() : null)
                .customerId(customerUserId)
                .customerUserId(customerUserId)
                .total(total)
                .notes(cart.getNotes())
                .items(itemResponses != null ? itemResponses : Collections.emptyList())
                .hasPriceChanges(hasPriceChanges)
                .hasUnavailableItems(hasUnavailableItems)
                .warnings(warnings != null ? warnings : Collections.emptyList())
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }
}
