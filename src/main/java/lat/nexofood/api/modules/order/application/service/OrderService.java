package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.modules.order.web.dto.request.OrderCreateRequest;
import lat.nexofood.api.modules.order.web.dto.response.OrderResponse;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    OrderResponse create(OrderCreateRequest request, UUID customerId);

    OrderResponse findByIdAndTenant(UUID orderId, UUID tenantId);

    List<OrderResponse> findAllByTenant(UUID tenantId);
}
