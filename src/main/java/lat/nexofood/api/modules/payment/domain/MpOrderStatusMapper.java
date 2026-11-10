package lat.nexofood.api.modules.payment.domain;

import java.util.Locale;

/**
 * Traduce el par {@code (status, status_detail)} devuelto por la Orders API de Mercado Pago
 * hacia el enum de dominio {@link PaymentStatus}.
 */
public final class MpOrderStatusMapper {

    private MpOrderStatusMapper() {
    }

    public static PaymentStatus toPaymentStatus(String mpStatus, String mpStatusDetail) {
        if (mpStatus == null || mpStatus.isBlank()) {
            return PaymentStatus.PENDING;
        }
        String normalizedStatus = mpStatus.trim().toLowerCase(Locale.ROOT);
        String normalizedDetail = mpStatusDetail != null
                ? mpStatusDetail.trim().toLowerCase(Locale.ROOT)
                : "";

        return switch (normalizedStatus) {
            case "created" -> PaymentStatus.PENDING;
            case "processing", "action_required" -> PaymentStatus.IN_PROCESS;
            case "processed" -> {
                if ("partially_refunded".equals(normalizedDetail) || "refunded".equals(normalizedDetail)) {
                    yield PaymentStatus.REFUNDED;
                }
                yield PaymentStatus.APPROVED;
            }
            case "refunded" -> PaymentStatus.REFUNDED;
            case "failed" -> PaymentStatus.REJECTED;
            case "canceled", "cancelled" -> {
                if ("expired".equals(normalizedDetail)) {
                    yield PaymentStatus.EXPIRED;
                }
                yield PaymentStatus.CANCELED;
            }
            case "expired" -> PaymentStatus.EXPIRED;
            case "charged_back" -> PaymentStatus.CHARGED_BACK;
            default -> PaymentStatus.PENDING;
        };
    }
}
