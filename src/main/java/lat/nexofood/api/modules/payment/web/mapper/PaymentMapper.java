package lat.nexofood.api.modules.payment.web.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lat.nexofood.api.modules.payment.domain.Payment;
import lat.nexofood.api.modules.payment.web.dto.response.PaymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mapper para la entidad {@link Payment}.
 * Sanitiza el {@code raw_response} del gateway de pagos antes de persistir,
 * eliminando datos sensibles (números de tarjeta, PII del pagador, tokens, etc.)
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
                .orderNumber(payment.getOrder() != null ? payment.getOrder().getOrderNumber() : null)
                .tenantId(payment.getTenant() != null ? payment.getTenant().getId() : null)
                .customerId(payment.getCustomerId())
                .mpOrderId(payment.getMpOrderId())
                .mpPaymentId(payment.getMpPaymentId())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus())
                .statusDetail(payment.getStatusDetail())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .expiresAt(payment.getExpiresAt())
                .approvedAt(payment.getApprovedAt())
                .refundedAt(payment.getRefundedAt())
                .refundReason(payment.getRefundReason())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    /**
     * Sanitiza el JSON crudo devuelto por Mercado Pago (Orders API o Payments API)
     * reteniendo únicamente los metadatos operativos y de conciliación.
     * Elimina datos de tarjeta, información personal del pagador y tokens sensibles.
     */
    public String sanitizePaymentResponse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawJson);
            ObjectNode sanitized = OBJECT_MAPPER.createObjectNode();

            // Campos seguros de Orders API y Payments API
            copyFieldIfPresent(root, sanitized, "id");
            copyFieldIfPresent(root, sanitized, "type");
            copyFieldIfPresent(root, sanitized, "processing_mode");
            copyFieldIfPresent(root, sanitized, "status");
            copyFieldIfPresent(root, sanitized, "status_detail");
            copyFieldIfPresent(root, sanitized, "external_reference");
            copyFieldIfPresent(root, sanitized, "total_amount");
            copyFieldIfPresent(root, sanitized, "total_paid_amount");
            copyFieldIfPresent(root, sanitized, "created_date");
            copyFieldIfPresent(root, sanitized, "last_updated_date");
            copyFieldIfPresent(root, sanitized, "payment_method_id");
            copyFieldIfPresent(root, sanitized, "payment_type_id");
            copyFieldIfPresent(root, sanitized, "transaction_amount");
            copyFieldIfPresent(root, sanitized, "currency_id");
            copyFieldIfPresent(root, sanitized, "date_created");
            copyFieldIfPresent(root, sanitized, "date_approved");
            copyFieldIfPresent(root, sanitized, "date_last_updated");
            copyFieldIfPresent(root, sanitized, "installments");
            copyFieldIfPresent(root, sanitized, "operation_type");

            if (root.has("transactions") && root.get("transactions").isObject()) {
                JsonNode txNode = root.get("transactions");
                if (txNode.has("payments") && txNode.get("payments").isArray()) {
                    ObjectNode sanitizedTransactions = OBJECT_MAPPER.createObjectNode();
                    ArrayNode sanitizedPayments = OBJECT_MAPPER.createArrayNode();
                    for (JsonNode paymentNode : txNode.get("payments")) {
                        ObjectNode safePayment = OBJECT_MAPPER.createObjectNode();
                        copyFieldIfPresent(paymentNode, safePayment, "id");
                        copyFieldIfPresent(paymentNode, safePayment, "amount");
                        copyFieldIfPresent(paymentNode, safePayment, "paid_amount");
                        copyFieldIfPresent(paymentNode, safePayment, "status");
                        copyFieldIfPresent(paymentNode, safePayment, "status_detail");
                        if (paymentNode.has("payment_method") && paymentNode.get("payment_method").isObject()) {
                            JsonNode pmNode = paymentNode.get("payment_method");
                            ObjectNode safePm = OBJECT_MAPPER.createObjectNode();
                            copyFieldIfPresent(pmNode, safePm, "id");
                            copyFieldIfPresent(pmNode, safePm, "type");
                            safePayment.set("payment_method", safePm);
                        }
                        sanitizedPayments.add(safePayment);
                    }
                    sanitizedTransactions.set("payments", sanitizedPayments);
                    sanitized.set("transactions", sanitizedTransactions);
                }
            }
            // EXCLUIDOS: payer, card, token, collector_id, checkout_url

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
