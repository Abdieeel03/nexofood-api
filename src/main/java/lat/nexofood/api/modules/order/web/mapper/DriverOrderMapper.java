package lat.nexofood.api.modules.order.web.mapper;

import lat.nexofood.api.common.util.GeoUtils;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderItem;
import lat.nexofood.api.modules.order.web.dto.response.DriverOrderItemResponse;
import lat.nexofood.api.modules.order.web.dto.response.DriverOrderResponse;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Mapper de órdenes para repartidor sin montos financieros, RUC ni correo electrónico del cliente.
 */
@Component
public class DriverOrderMapper {

    public DriverOrderResponse toResponse(Order order) {
        if (order == null) {
            return null;
        }

        List<DriverOrderItemResponse> itemResponses = order.getItems() != null
                ? order.getItems().stream().map(this::toItemResponse).toList()
                : Collections.emptyList();

        String customerFullName = null;
        String customerPhone = null;
        TenantCustomer customer = order.getCustomer();
        if (customer != null && customer.getUser() != null) {
            User customerUser = customer.getUser();
            customerFullName = customerUser.getFullName();
            customerPhone = customerUser.getPhone();
        }

        Tenant tenant = order.getTenant();

        return DriverOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .customerFullName(customerFullName)
                .customerPhone(customerPhone)
                .deliveryAddress(order.getDeliveryAddress())
                .deliveryLatitude(GeoUtils.getLatitude(order.getDeliveryLocation()))
                .deliveryLongitude(GeoUtils.getLongitude(order.getDeliveryLocation()))
                .pickupStoreName(tenant != null ? tenant.getName() : null)
                .pickupAddress(tenant != null ? tenant.getAddress() : null)
                .pickupPhone(tenant != null ? tenant.getPhone() : null)
                .pickupLatitude(tenant != null ? GeoUtils.getLatitude(tenant.getLocation()) : null)
                .pickupLongitude(tenant != null ? GeoUtils.getLongitude(tenant.getLocation()) : null)
                .estimatedDistanceKm(order.getEstimatedDistanceKm())
                .estimatedDeliveryMinutes(order.getEstimatedDeliveryMinutes())
                .estimatedArrivalAt(order.getEstimatedArrivalAt())
                .etaMessage(OrderMapper.resolveEtaMessage(order))
                .dispatchedAt(order.getDispatchedAt())
                .deliveredAt(order.getDeliveredAt())
                .notes(order.getNotes())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    public DriverOrderItemResponse toItemResponse(OrderItem item) {
        if (item == null) {
            return null;
        }
        return DriverOrderItemResponse.builder()
                .id(item.getId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .notes(item.getNotes())
                .build();
    }
}
