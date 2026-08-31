package lat.nexofood.api.modules.auth.repository;

import lat.nexofood.api.modules.auth.domain.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

    List<CustomerAddress> findAllByUserId(UUID userId);

    Optional<CustomerAddress> findByIdAndUserId(UUID id, UUID userId);
}
