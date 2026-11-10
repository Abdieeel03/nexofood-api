package lat.nexofood.api.modules.payment.application.scheduler;

import lat.nexofood.api.modules.payment.application.service.OrderPaymentService;
import lat.nexofood.api.modules.payment.domain.Payment;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lat.nexofood.api.modules.payment.infrastructure.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private static final List<PaymentStatus> RECONCILABLE_STATUSES = List.of(
            PaymentStatus.PENDING,
            PaymentStatus.IN_PROCESS,
            PaymentStatus.REFUND_PENDING
    );

    private final PaymentRepository paymentRepository;
    private final OrderPaymentService orderPaymentService;

    @Scheduled(cron = "${mercadopago.reconciliation-cron:0 */5 * * * *}")
    public int reconcilePendingPayments() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime updatedBefore = now.minusMinutes(5);
        OffsetDateTime createdAfter = now.minusHours(48);

        List<Payment> stalePayments = paymentRepository.findTop100ByStatusInAndUpdatedAtBeforeAndCreatedAtAfter(
                RECONCILABLE_STATUSES,
                updatedBefore,
                createdAfter
        );

        int processed = 0;
        for (Payment payment : stalePayments) {
            if (payment.getMpOrderId() != null && !payment.getMpOrderId().isBlank()) {
                try {
                    orderPaymentService.processMpOrderNotification(payment.getMpOrderId());
                    processed++;
                } catch (Exception ex) {
                    log.warn("Error reconciliando pago {} (mpOrderId={}): {}",
                            payment.getId(), payment.getMpOrderId(), ex.getMessage());
                }
            }
        }
        return processed;
    }
}
