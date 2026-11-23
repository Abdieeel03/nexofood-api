package lat.nexofood.api.modules.order.web.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record DriverOrderItemResponse(
        UUID id,
        String productName,
        Integer quantity,
        String notes
) {}
