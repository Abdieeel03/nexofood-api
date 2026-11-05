package lat.nexofood.api.modules.inventory.application.service;

import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.inventory.domain.InventoryUnit;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryItemRequest;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryItemResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface InventoryItemService {

    PageResponse<InventoryItemResponse> findByTenantPaginated(
            UUID tenantId,
            Boolean activeOnly,
            String category,
            InventoryUnit unit,
            Pageable pageable
    );

    List<InventoryItemResponse> findAllByTenant(UUID tenantId, boolean activeOnly);

    InventoryItemResponse findByIdAndTenant(UUID tenantId, UUID itemId);

    InventoryItemResponse create(UUID tenantId, InventoryItemRequest request);

    InventoryItemResponse update(UUID tenantId, UUID itemId, InventoryItemRequest request);

    InventoryItemResponse toggleStatus(UUID tenantId, UUID itemId, boolean isActive);

    void delete(UUID tenantId, UUID itemId);
}
