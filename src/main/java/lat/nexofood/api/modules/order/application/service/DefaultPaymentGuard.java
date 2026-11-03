package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.payment.domain.Payment;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lat.nexofood.api.modules.payment.infrastructure.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DefaultPaymentGuard implements PaymentGuard {

    private final PaymentRepository paymentRepository;

    @Override
    @Transactional(readOnly = true)
    public void assertCanMoveToPreparation(Order order) {
        if (order == null || order.getId() == null) {
            return;
        }
        Optional<Payment> paymentOpt = paymentRepository.findByOrderId(order.getId());
        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            if (payment.getStatus() != PaymentStatus.APPROVED) {
                throw new ResourceConflictException(
                        "El pedido no puede pasar a preparación porque el pago asociado no está aprobado (estado actual: "
                                + payment.getStatus() + ")"
                );
            }
        }
    }
}
