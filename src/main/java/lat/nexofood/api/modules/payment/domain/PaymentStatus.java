package lat.nexofood.api.modules.payment.domain;

public enum PaymentStatus {
    PENDING,
    IN_PROCESS,
    APPROVED,
    REJECTED,
    CANCELED,
    EXPIRED,
    REFUND_PENDING,
    REFUNDED,
    CHARGED_BACK
}
