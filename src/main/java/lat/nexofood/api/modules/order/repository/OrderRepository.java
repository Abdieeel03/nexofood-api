package lat.nexofood.api.modules.order.repository;

import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findAllByTenantId(UUID tenantId);

    List<Order> findAllByTenantIdAndStatus(UUID tenantId, OrderStatus status);

    Optional<Order> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Order> findAllByCustomerId(UUID customerId);

    List<Order> findAllByDeliveryStaffId(UUID deliveryStaffId);

    boolean existsByOrderNumberAndTenantId(String orderNumber, UUID tenantId);
}
