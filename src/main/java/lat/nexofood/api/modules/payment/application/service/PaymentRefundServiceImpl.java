package lat.nexofood.api.modules.payment.application.service;

import lat.nexofood.api.common.exception.PaymentGatewayException;
import lat.nexofood.api.common.exception.ResourceConflictException;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.payment.domain.MpOrderStatusMapper;
import lat.nexofood.api.modules.payment.domain.Payment;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.MercadoPagoClient;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderResponse;
import lat.nexofood.api.modules.payment.infrastructure.repository.PaymentRepository;
import lat.nexofood.api.modules.payment.web.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundServiceImpl implements PaymentRefundService {

    private final PaymentRepository paymentRepository;
    private final MercadoPagoClient mercadoPagoClient;
    private final MercadoPagoOAuthService oauthService;
    private final PaymentMapper paymentMapper;

    @Override
    @Transactional
    public void refundOrderPayment(Order order, String reason) {
        if (order == null || order.getId() == null) {
            return;
        }
        Optional<Payment> paymentOpt = paymentRepository.findByOrderIdForUpdate(order.getId())
                .or(() -> paymentRepository.findByOrderId(order.getId()));

        if (paymentOpt.isEmpty()) {
            return;
        }

        Payment payment = paymentOpt.get();
        if (payment.isAlreadyRefundedOrPendingRefund()) {
            log.info("El pago {} de la orden {} ya fue reembolsado o está en proceso de reembolso",
                    payment.getId(), order.getId());
            return;
        }

        if (!payment.canBeRefunded()) {
            throw new ResourceConflictException(
                    "El pago asociado al pedido no se encuentra en un estado reembolsable (estado actual: "
                            + payment.getStatus() + ")"
            );
        }

        refundPaymentDirectly(payment, reason);
    }

    @Override
    @Transactional
    public void refundPaymentDirectly(Payment payment, String reason) {
        if (payment == null || payment.isAlreadyRefundedOrPendingRefund()) {
            return;
        }
        if (payment.getMpOrderId() == null || payment.getMpOrderId().isBlank()) {
            throw new PaymentGatewayException("El pago no cuenta con un identificador de orden en Mercado Pago (mpOrderId)");
        }

        String accessToken = oauthService.getValidAccessToken(payment.getTenant());
        String idempotencyKey = "refund-" + payment.getId();

        MpOrderResponse refundResponse = mercadoPagoClient.refundOrder(
                accessToken,
                idempotencyKey,
                payment.getMpOrderId()
        );

        PaymentStatus mappedStatus = refundResponse != null
                ? MpOrderStatusMapper.toPaymentStatus(refundResponse.status(), refundResponse.statusDetail())
                : PaymentStatus.REFUNDED;

        if (mappedStatus == PaymentStatus.IN_PROCESS) {
            payment.setStatus(PaymentStatus.REFUND_PENDING);
        } else {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setRefundedAt(OffsetDateTime.now());
        }

        if (refundResponse != null) {
            payment.setStatusDetail(refundResponse.statusDetail());
            if (refundResponse.rawJson() != null) {
                payment.setRawResponse(paymentMapper.sanitizePaymentResponse(refundResponse.rawJson()));
            }
        }
        payment.setRefundReason(reason != null && !reason.isBlank() ? reason.trim() : "Pedido cancelado");

        paymentRepository.save(payment);
        log.info("Reembolso ejecutado en Mercado Pago para el pago {} (mpOrderId={}, nuevo estado={})",
                payment.getId(), payment.getMpOrderId(), payment.getStatus());
    }
}
