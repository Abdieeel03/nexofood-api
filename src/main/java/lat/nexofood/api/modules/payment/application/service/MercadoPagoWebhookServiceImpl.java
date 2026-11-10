package lat.nexofood.api.modules.payment.application.service;

import lat.nexofood.api.modules.subscription.application.service.subscription.SubscriptionBillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class MercadoPagoWebhookServiceImpl implements MercadoPagoWebhookService {

    private final OrderPaymentService orderPaymentService;
    private final SubscriptionBillingService subscriptionBillingService;

    @Override
    public void handleNotification(String type, String dataId) {
        if (type == null || type.isBlank() || dataId == null || dataId.isBlank()) {
            log.debug("Webhook de Mercado Pago ignorado por falta de type o data.id (type={}, dataId={})", type, dataId);
            return;
        }

        String normalizedType = type.trim().toLowerCase(Locale.ROOT);
        switch (normalizedType) {
            case "order" -> orderPaymentService.processMpOrderNotification(dataId.trim());
            case "subscription_preapproval" -> subscriptionBillingService.processPreapprovalNotification(dataId.trim());
            case "subscription_authorized_payment" -> subscriptionBillingService.processAuthorizedPaymentNotification(dataId.trim());
            default -> log.debug("Tópico de webhook Mercado Pago no manejado: type={}, data.id={}", type, dataId);
        }
    }
}
