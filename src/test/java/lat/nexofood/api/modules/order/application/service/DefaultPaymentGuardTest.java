package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.payment.domain.Payment;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lat.nexofood.api.modules.payment.infrastructure.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPaymentGuardTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private DefaultPaymentGuard paymentGuard;

    @Test
    @DisplayName("assertCanMoveToPreparation: bloquea órdenes sin pago o con pago no aprobado, y permite solo APPROVED")
    void enforcesStrictApprovedPaymentRequirement() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).build();

        // 1. Sin pago registrado -> lanza conflicto
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        assertThrows(ResourceConflictException.class, () -> paymentGuard.assertCanMoveToPreparation(order));

        // 2. Pago en estado PENDING -> lanza conflicto
        Payment payment = Payment.builder().id(UUID.randomUUID()).order(order).status(PaymentStatus.PENDING).build();
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        assertThrows(ResourceConflictException.class, () -> paymentGuard.assertCanMoveToPreparation(order));

        // 3. Pago en estado APPROVED -> permite pasar a cocina
        payment.setStatus(PaymentStatus.APPROVED);
        assertDoesNotThrow(() -> paymentGuard.assertCanMoveToPreparation(order));
    }
}
