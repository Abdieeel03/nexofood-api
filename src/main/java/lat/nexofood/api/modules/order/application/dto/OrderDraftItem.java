package lat.nexofood.api.modules.order.application.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record OrderDraftItem(
        UUID productId,
        String productName,
        UUID priceId,
        UUID taxId,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineSubtotal,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        String notes
) {

    public BigDecimal lineTotalWithTax() {
        BigDecimal sub = lineSubtotal != null ? lineSubtotal : BigDecimal.ZERO;
        BigDecimal tax = taxAmount != null ? taxAmount : BigDecimal.ZERO;
        return sub.add(tax);
    }
}
