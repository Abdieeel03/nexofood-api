package lat.nexofood.api.modules.inventory.application.service;

import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementReason;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementType;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryMovementRequest;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryMovementResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InventoryMovementService {

    InventoryMovementResponse register(UUID tenantId, InventoryMovementRequest request);

    PageResponse<InventoryMovementResponse> getKardexByStock(
            UUID tenantId,
            UUID stockId,
            Pageable pageable
    );

    PageResponse<InventoryMovementResponse> findByTenantPaginated(
            UUID tenantId,
            InventoryMovementType movementType,
            InventoryMovementReason reason,
            Pageable pageable
    );
}
