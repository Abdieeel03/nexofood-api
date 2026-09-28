package lat.nexofood.api.modules.catalog.infrastructure.repository;

import lat.nexofood.api.modules.catalog.domain.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductPriceRepository extends JpaRepository<ProductPrice, UUID> {

    List<ProductPrice> findAllByProductId(UUID productId);

    List<ProductPrice> findAllByProductIdAndIsActiveTrue(UUID productId);

    Optional<ProductPrice> findByProductIdAndIsBaseTrue(UUID productId);
}
