package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.modules.order.web.dto.request.OrderCheckoutRequest;
import lat.nexofood.api.modules.order.web.dto.response.OrderResponse;

public interface CheckoutService {

    OrderResponse createFromCart(String slug, OrderCheckoutRequest request);
}
