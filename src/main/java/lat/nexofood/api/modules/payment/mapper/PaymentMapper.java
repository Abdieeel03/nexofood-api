package lat.nexofood.api.modules.payment.mapper;

import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.payment.domain.Payment;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lat.nexofood.api.modules.payment.dto.request.PaymentCreateRequest;
import lat.nexofood.api.modules.payment.dto.response.PaymentResponse;
import lat.nexofood.api.modules.tenant.domain.Tenant;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        if (payment == null) {
            return null;
        }
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrder() != null ? payment.getOrder().getId() : null)
                .tenantId(payment.getTenant() != null ? payment.getTenant().getId() : null)
                .mpPaymentId(payment.getMpPaymentId())
                .mpPreferenceId(payment.getMpPreferenceId())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    public Payment toEntity(PaymentCreateRequest request, Order order, Tenant tenant) {
        if (request == null) {
            return null;
        }
        return Payment.builder()
                .order(order)
                .tenant(tenant)
                .mpPaymentId(request.mpPaymentId())
                .mpPreferenceId(request.mpPreferenceId())
                .paymentMethod(request.paymentMethod())
                .status(PaymentStatus.PENDING)
                .amount(request.amount())
                .rawResponse(request.rawResponse())
                .build();
    }
}
