package lat.nexofood.api.modules.cart.infrastructure.repository;

import lat.nexofood.api.modules.cart.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByTenantIdAndCustomerId(UUID tenantId, UUID customerId);

    Optional<Cart> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Cart> findAllByTenantId(UUID tenantId);

    List<Cart> findAllByCustomerId(UUID customerId);

    boolean existsByTenantIdAndCustomerId(UUID tenantId, UUID customerId);

    void deleteByTenantIdAndCustomerId(UUID tenantId, UUID customerId);
}
