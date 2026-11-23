package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.web.dto.response.KitchenOrderResponse;

import java.util.List;
import java.util.UUID;

public interface KitchenOrderService {

    List<KitchenOrderResponse> getOrders(UUID tenantId, OrderStatus status);

    KitchenOrderResponse getOrderById(UUID tenantId, UUID orderId);

    KitchenOrderResponse updateStatus(UUID tenantId, UUID orderId, OrderStatus status);
}
