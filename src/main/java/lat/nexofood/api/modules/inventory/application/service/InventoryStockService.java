package lat.nexofood.api.modules.inventory.application.service;

import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryStockRequest;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryStockUpdateRequest;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryStockResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface InventoryStockService {

    PageResponse<InventoryStockResponse> findByTenantPaginated(
            UUID tenantId,
            Boolean activeOnly,
            Pageable pageable
    );

    List<InventoryStockResponse> findLowStockAlerts(UUID tenantId);

    PageResponse<InventoryStockResponse> findLowStockAlertsPaginated(UUID tenantId, Pageable pageable);

    InventoryStockResponse findByIdAndTenant(UUID tenantId, UUID stockId);

    InventoryStockResponse findByItemAndTenant(UUID tenantId, UUID itemId);

    InventoryStockResponse create(UUID tenantId, InventoryStockRequest request);

    InventoryStockResponse update(UUID tenantId, UUID stockId, InventoryStockUpdateRequest request);
}
