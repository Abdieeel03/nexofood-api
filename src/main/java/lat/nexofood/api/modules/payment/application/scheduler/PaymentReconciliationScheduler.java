package lat.nexofood.api.modules.payment.application.scheduler;

import lat.nexofood.api.modules.payment.application.service.OrderPaymentService;
import lat.nexofood.api.modules.payment.domain.Payment;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lat.nexofood.api.modules.payment.infrastructure.repository.PaymentRepository;
import lat.nexofood.api.modules.subscription.application.service.subscription.SubscriptionBillingService;
import lat.nexofood.api.modules.subscription.domain.Subscription;
import lat.nexofood.api.modules.subscription.domain.SubscriptionStatus;
import lat.nexofood.api.modules.subscription.infrastructure.repository.SubscriptionRepository;
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
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionBillingService subscriptionBillingService;

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

        // Reconciliación periódica de suscripciones en estado PENDING
        try {
            List<Subscription> pendingSubscriptions = subscriptionRepository
                    .findAllByStatusAndMpPreapprovalIdIsNotNull(SubscriptionStatus.PENDING);
            for (Subscription sub : pendingSubscriptions) {
                if (sub.getMpPreapprovalId() != null && !sub.getMpPreapprovalId().isBlank()) {
                    try {
                        subscriptionBillingService.processPreapprovalNotification(sub.getMpPreapprovalId());
                        processed++;
                    } catch (Exception ex) {
                        log.warn("Error reconciliando suscripción {} (mpPreapprovalId={}): {}",
                                sub.getId(), sub.getMpPreapprovalId(), ex.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Error buscando suscripciones pendientes para reconciliación: {}", ex.getMessage());
        }

        return processed;
    }
}
