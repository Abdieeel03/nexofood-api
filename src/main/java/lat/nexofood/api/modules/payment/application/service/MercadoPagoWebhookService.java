package lat.nexofood.api.modules.payment.application.service;

public interface MercadoPagoWebhookService {

    void handleNotification(String type, String dataId);
}
