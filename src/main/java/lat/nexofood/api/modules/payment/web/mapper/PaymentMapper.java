package lat.nexofood.api.modules.payment.web.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.payment.domain.Payment;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import lat.nexofood.api.modules.payment.web.dto.request.PaymentCreateRequest;
import lat.nexofood.api.modules.payment.web.dto.response.PaymentResponse;
import lat.nexofood.api.modules.store.domain.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mapper para la entidad Payment.
 * Sanitiza el raw_response del gateway de pagos antes de persistir,
 * eliminando datos sensibles (números de tarjeta, PII del pagador, etc.)
 * para cumplir con PCI-DSS.
 */
@Slf4j
@Component
public class PaymentMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
                .rawResponse(sanitizePaymentResponse(request.rawResponse()))
                .build();
    }

    /**
     * Sanitiza el JSON de respuesta del gateway de pagos reteniendo únicamente
     * los metadatos operativos y de conciliación. Elimina datos de tarjeta,
     * información personal del pagador y tokens sensibles.
     *
     * @param rawJson JSON crudo devuelto por Mercado Pago
     * @return JSON sanitizado con solo campos seguros, o null si la entrada es nula
     */
    private String sanitizePaymentResponse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawJson);
            ObjectNode sanitized = OBJECT_MAPPER.createObjectNode();

            // Campos seguros para operaciones y conciliación
            copyFieldIfPresent(root, sanitized, "id");
            copyFieldIfPresent(root, sanitized, "status");
            copyFieldIfPresent(root, sanitized, "status_detail");
            copyFieldIfPresent(root, sanitized, "payment_method_id");
            copyFieldIfPresent(root, sanitized, "payment_type_id");
            copyFieldIfPresent(root, sanitized, "transaction_amount");
            copyFieldIfPresent(root, sanitized, "currency_id");
            copyFieldIfPresent(root, sanitized, "date_created");
            copyFieldIfPresent(root, sanitized, "date_approved");
            copyFieldIfPresent(root, sanitized, "date_last_updated");
            copyFieldIfPresent(root, sanitized, "external_reference");
            copyFieldIfPresent(root, sanitized, "installments");
            copyFieldIfPresent(root, sanitized, "issuer_id");
            copyFieldIfPresent(root, sanitized, "operation_type");
            copyFieldIfPresent(root, sanitized, "order");
            // EXCLUIDOS: card, payer.email, payer.identification, token, collector_id

            return OBJECT_MAPPER.writeValueAsString(sanitized);
        } catch (Exception e) {
            log.warn("No se pudo parsear raw_response del pago, se descarta para proteger datos sensibles.", e);
            return null;
        }
    }

    private void copyFieldIfPresent(JsonNode source, ObjectNode target, String fieldName) {
        if (source.has(fieldName) && !source.get(fieldName).isNull()) {
            target.set(fieldName, source.get(fieldName));
        }
    }
}
