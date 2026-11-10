package lat.nexofood.api.modules.payment.application.service;

import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.payment.domain.Payment;

public interface PaymentRefundService {

    void refundOrderPayment(Order order, String reason);

    void refundPaymentDirectly(Payment payment, String reason);
}
