package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.web.dto.response.DriverOrderResponse;

import java.util.List;
import java.util.UUID;

public interface DriverOrderService {

    List<DriverOrderResponse> getOrders(UUID tenantId, OrderStatus status);

    DriverOrderResponse getOrderById(UUID tenantId, UUID orderId);

    DriverOrderResponse updateStatus(UUID tenantId, UUID orderId, OrderStatus status);
}
