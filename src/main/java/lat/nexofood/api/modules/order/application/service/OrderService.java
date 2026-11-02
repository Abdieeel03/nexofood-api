package lat.nexofood.api.modules.order.application.service;

import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.order.domain.DeliveryType;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.order.web.dto.request.OrderCreateRequest;
import lat.nexofood.api.modules.order.web.dto.response.OrderResponse;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderService {

    OrderResponse create(OrderCreateRequest request, UUID customerId);

    OrderResponse createFromResolvedCustomer(OrderCreateRequest request, String ruc, TenantCustomer customer);

    OrderResponse findByIdAndTenant(UUID orderId, UUID tenantId);

    List<OrderResponse> findAllByTenant(UUID tenantId);

    PageResponse<OrderResponse> findByTenantPaginated(
            UUID tenantId,
            OrderStatus status,
            DeliveryType deliveryType,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            Pageable pageable
    );

    List<OrderResponse> getKitchenOrders(UUID tenantId);

    List<OrderResponse> getMyAssignedDeliveries(UUID tenantId, OrderStatus status);

    PageResponse<OrderResponse> getMyOrdersBySlug(String slug, Pageable pageable);

    OrderResponse getMyOrderDetailBySlug(String slug, UUID orderId);

    OrderResponse updateStatus(UUID tenantId, UUID orderId, OrderStatus newStatus);

    OrderResponse assignDeliveryStaff(UUID tenantId, UUID orderId, UUID deliveryStaffUserId);
}
