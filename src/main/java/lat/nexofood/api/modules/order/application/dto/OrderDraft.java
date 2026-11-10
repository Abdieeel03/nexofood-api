package lat.nexofood.api.modules.order.application.dto;

import lat.nexofood.api.modules.order.domain.DeliveryType;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record OrderDraft(
        UUID tenantId,
        UUID customerUserId,
        DeliveryType deliveryType,
        UUID customerAddressId,
        String deliveryAddress,
        Double deliveryLatitude,
        Double deliveryLongitude,
        String ruc,
        String notes,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal deliveryFee,
        BigDecimal total,
        List<OrderDraftItem> items
) {}
