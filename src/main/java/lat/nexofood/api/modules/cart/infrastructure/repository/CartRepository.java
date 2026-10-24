package lat.nexofood.api.modules.cart.infrastructure.repository;

import lat.nexofood.api.modules.cart.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByCustomerUserId(UUID customerId);

    Optional<Cart> findByTenantIdAndCustomerUserId(UUID tenantId, UUID userId);

    Optional<Cart> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Cart> findAllByTenantId(UUID tenantId);

    List<Cart> findAllByCustomerUserId(UUID userId);

    boolean existsByTenantIdAndCustomerUserId(UUID tenantId, UUID customerId);

    void deleteByCustomerUserId(UUID customerId);

    void deleteByTenantIdAndCustomerUserId(UUID tenantId, UUID customerId);
}
