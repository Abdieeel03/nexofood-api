package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.modules.cart.domain.Cart;
import lat.nexofood.api.modules.cart.domain.CartItem;
import lat.nexofood.api.modules.cart.infrastructure.repository.CartRepository;
import lat.nexofood.api.modules.order.application.dto.OrderDraft;
import lat.nexofood.api.modules.order.web.dto.request.OrderCheckoutRequest;
import lat.nexofood.api.modules.order.web.dto.request.OrderCreateRequest;
import lat.nexofood.api.modules.order.web.dto.request.OrderItemRequest;
import lat.nexofood.api.modules.payment.application.service.OrderPaymentService;
import lat.nexofood.api.modules.payment.web.dto.response.CheckoutPaymentResponse;
import lat.nexofood.api.modules.store.application.service.customer.CurrentCustomerResolver;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {

    private final CurrentCustomerResolver currentCustomerResolver;
    private final CartRepository cartRepository;
    private final OrderService orderService;
    private final OrderPaymentService orderPaymentService;

    @Override
    @Transactional
    public CheckoutPaymentResponse createFromCart(String slug, OrderCheckoutRequest request) {
        if (request == null || request.deliveryType() == null) {
            throw new BadRequestException("Los datos de entrega son obligatorios para completar el checkout");
        }

        TenantCustomer customer = currentCustomerResolver.resolveBySlug(slug);
        Tenant tenant = customer.getTenant();
        UUID userId = customer.getUser() != null && customer.getUser().getId() != null
                ? customer.getUser().getId()
                : customer.getId().getUserId();

        Cart cart = cartRepository.findByTenantIdAndCustomerUserId(tenant.getId(), userId)
                .orElseThrow(() -> new BadRequestException(
                        "Tu carrito está vacío. Agrega productos antes de realizar el pedido."
                ));

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BadRequestException("Tu carrito está vacío. Agrega productos antes de realizar el pedido.");
        }

        List<OrderItemRequest> itemRequests = new ArrayList<>();
        for (CartItem cartItem : cart.getItems()) {
            if (cartItem.getProduct() == null || cartItem.getProduct().getId() == null) {
                throw new BadRequestException("Uno de los productos de tu carrito ya no existe en el catálogo");
            }
            itemRequests.add(
                    OrderItemRequest.builder()
                            .productId(cartItem.getProduct().getId())
                            .quantity(cartItem.getQuantity())
                            .notes(cartItem.getNotes())
                            .build()
            );
        }

        String resolvedNotes = (request.notes() != null && !request.notes().isBlank())
                ? request.notes()
                : cart.getNotes();

        OrderCreateRequest createRequest = OrderCreateRequest.builder()
                .tenantId(tenant.getId())
                .deliveryType(request.deliveryType())
                .customerAddressId(request.customerAddressId())
                .deliveryAddress(request.deliveryAddress())
                .deliveryLatitude(request.deliveryLatitude())
                .deliveryLongitude(request.deliveryLongitude())
                .notes(resolvedNotes)
                .items(itemRequests)
                .build();

        OrderDraft draft = orderService.buildDraft(
                createRequest,
                request.ruc(),
                customer
        );

        // El carrito se conserva hasta que el webhook confirme el pago aprobado
        return orderPaymentService.startCheckout(draft, customer);
    }
}
