package lat.nexofood.api.modules.payment.infrastructure.mercadopago;

import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpAuthorizedPaymentResponse;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOAuthTokenResponse;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderRequest;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpOrderResponse;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpPreapprovalRequest;
import lat.nexofood.api.modules.payment.infrastructure.mercadopago.dto.MpPreapprovalResponse;

public interface MercadoPagoClient {

    MpOrderResponse createOrder(String accessToken, String idempotencyKey, MpOrderRequest request);

    MpOrderResponse getOrder(String accessToken, String mpOrderId);

    MpOrderResponse refundOrder(String accessToken, String idempotencyKey, String mpOrderId);

    MpOrderResponse cancelOrder(String accessToken, String idempotencyKey, String mpOrderId);

    MpPreapprovalResponse createPreapproval(MpPreapprovalRequest request);

    MpPreapprovalResponse getPreapproval(String preapprovalId);

    MpPreapprovalResponse updatePreapprovalStatus(String preapprovalId, String status);

    MpAuthorizedPaymentResponse getAuthorizedPayment(String authorizedPaymentId);

    MpOAuthTokenResponse exchangeAuthorizationCode(String code);

    MpOAuthTokenResponse refreshToken(String refreshToken);
}
