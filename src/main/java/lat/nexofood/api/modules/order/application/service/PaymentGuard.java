package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.modules.order.domain.Order;

public interface PaymentGuard {

    void assertCanMoveToPreparation(Order order);
}
