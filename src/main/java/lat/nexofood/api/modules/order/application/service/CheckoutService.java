package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.modules.order.web.dto.request.OrderCheckoutRequest;
import lat.nexofood.api.modules.payment.web.dto.response.CheckoutPaymentResponse;

public interface CheckoutService {

    CheckoutPaymentResponse createFromCart(String slug, OrderCheckoutRequest request);
}
