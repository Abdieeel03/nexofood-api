package lat.nexofood.api.common.exception;

import lombok.Getter;

@Getter
public class PaymentGatewayException extends RuntimeException {

    private final Integer gatewayStatus;

    public PaymentGatewayException(String message) {
        super(message);
        this.gatewayStatus = null;
    }

    public PaymentGatewayException(String message, Integer gatewayStatus) {
        super(message);
        this.gatewayStatus = gatewayStatus;
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
        this.gatewayStatus = null;
    }

    public PaymentGatewayException(String message, Integer gatewayStatus, Throwable cause) {
        super(message, cause);
        this.gatewayStatus = gatewayStatus;
    }
}
