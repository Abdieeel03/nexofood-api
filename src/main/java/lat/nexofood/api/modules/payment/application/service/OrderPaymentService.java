package lat.nexofood.api.modules.payment.application.service;

import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.order.application.dto.OrderDraft;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lat.nexofood.api.modules.payment.web.dto.response.CheckoutPaymentResponse;
import lat.nexofood.api.modules.payment.web.dto.response.PaymentResponse;
import lat.nexofood.api.modules.payment.web.dto.response.PaymentStatusResponse;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderPaymentService {

    CheckoutPaymentResponse startCheckout(OrderDraft draft, TenantCustomer customer);

    void processMpOrderNotification(String mpOrderId);

    PaymentStatusResponse getCustomerPaymentStatus(String slug, UUID paymentId);

    PageResponse<PaymentResponse> findByTenantPaginated(UUID tenantId, PaymentStatus status, Pageable pageable);

    PaymentResponse findByOrderIdAndTenant(UUID orderId, UUID tenantId);
}
