package lat.nexofood.api.modules.inventory.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lat.nexofood.api.common.exception.GlobalExceptionHandler;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.modules.inventory.application.service.InventoryItemService;
import lat.nexofood.api.modules.inventory.application.service.InventoryMovementService;
import lat.nexofood.api.modules.inventory.application.service.InventoryStockService;
import lat.nexofood.api.modules.inventory.application.service.ProductIngredientService;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementReason;
import lat.nexofood.api.modules.inventory.domain.InventoryMovementType;
import lat.nexofood.api.modules.inventory.domain.InventoryUnit;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryItemRequest;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryMovementRequest;
import lat.nexofood.api.modules.inventory.web.dto.request.InventoryStockRequest;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryItemResponse;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryMovementResponse;
import lat.nexofood.api.modules.inventory.web.dto.response.InventoryStockResponse;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
class InventoryAuthorizationControllerTest {

    private InventoryItemService inventoryItemService;
    private InventoryStockService inventoryStockService;
    private InventoryMovementService inventoryMovementService;
    private ProductIngredientService productIngredientService;
    private TenantAccessService tenantAccessService;

    private InventoryItemController proxiedItemController;
    private InventoryStockController proxiedStockController;
    private InventoryMovementController proxiedMovementController;
    private ProductIngredientController proxiedRecipeController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        inventoryItemService = mock(InventoryItemService.class);
        inventoryStockService = mock(InventoryStockService.class);
        inventoryMovementService = mock(InventoryMovementService.class);
        productIngredientService = mock(ProductIngredientService.class);
        tenantAccessService = mock(TenantAccessService.class);

        StaticApplicationContext appContext = new StaticApplicationContext();
        appContext.getBeanFactory().registerSingleton("tenantAccess", tenantAccessService);
        appContext.refresh();

        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setApplicationContext(appContext);

        PreAuthorizeAuthorizationManager authorizationManager = new PreAuthorizeAuthorizationManager();
        authorizationManager.setExpressionHandler(expressionHandler);

        AuthorizationManagerBeforeMethodInterceptor interceptor =
                AuthorizationManagerBeforeMethodInterceptor.preAuthorize(authorizationManager);

        proxiedItemController = createSecuredProxy(new InventoryItemController(inventoryItemService), interceptor);
        proxiedStockController = createSecuredProxy(
                new InventoryStockController(inventoryStockService, inventoryMovementService), interceptor
        );
        proxiedMovementController = createSecuredProxy(
                new InventoryMovementController(inventoryMovementService), interceptor
        );
        proxiedRecipeController = createSecuredProxy(
                new ProductIngredientController(productIngredientService), interceptor
        );

        mockMvc = MockMvcBuilders.standaloneSetup(
                        proxiedItemController,
                        proxiedStockController,
                        proxiedMovementController,
                        proxiedRecipeController
                )
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T> T createSecuredProxy(T target, AuthorizationManagerBeforeMethodInterceptor interceptor) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return (T) factory.getProxy();
    }

    @Test
    @DisplayName("InventoryControllers: protegen creación de insumos/stock (OWNER/ADMIN), movimientos (OWNER/ADMIN/COCINERO) y Kardex")
    void inventoryControllersEnforceTenantRoleSecurityAndEndpointsWork() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID stockId = UUID.randomUUID();

        // 1. Usuario que no es miembro no puede consultar insumos ni alertas de stock
        when(tenantAccessService.isMember(tenantId)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () ->
                proxiedItemController.getItemById(tenantId, itemId)
        );
        assertThrows(AccessDeniedException.class, () ->
                proxiedStockController.getLowStockAlerts(tenantId)
        );

        // 2. Usuario sin rol OWNER/ADMIN no puede crear insumo ni inicializar stock
        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(false);
        assertThrows(AccessDeniedException.class, () ->
                proxiedItemController.createItem(tenantId, InventoryItemRequest.builder().name("Harina").build())
        );
        assertThrows(AccessDeniedException.class, () ->
                proxiedStockController.createStock(tenantId, InventoryStockRequest.builder().itemId(itemId).build())
        );

        // 3. OWNER/ADMIN crea insumo (201) e inicializa stock (201)
        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(true);
        when(inventoryItemService.create(eq(tenantId), any())).thenReturn(
                InventoryItemResponse.builder()
                        .id(itemId)
                        .tenantId(tenantId)
                        .name("Harina 00")
                        .sku("INS-HAR-01")
                        .unit(InventoryUnit.KG)
                        .isActive(true)
                        .build()
        );
        when(inventoryStockService.create(eq(tenantId), any())).thenReturn(
                InventoryStockResponse.builder()
                        .id(stockId)
                        .tenantId(tenantId)
                        .itemId(itemId)
                        .quantity(new BigDecimal("0.000"))
                        .minimumStock(new BigDecimal("5.000"))
                        .lowStock(true)
                        .build()
        );

        mockMvc.perform(post("/api/v1/tenants/" + tenantId + "/inventory/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                InventoryItemRequest.builder().name("Harina 00").sku("INS-HAR-01").unit(InventoryUnit.KG).build()
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sku").value("INS-HAR-01"));

        mockMvc.perform(post("/api/v1/tenants/" + tenantId + "/inventory/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                InventoryStockRequest.builder().itemId(itemId).minimumStock(new BigDecimal("5.000")).build()
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lowStock").value(true));

        // 4. COCINERO, ADMIN u OWNER registra movimiento de inventario (201) y consulta Kardex (200)
        when(tenantAccessService.hasAnyRole(
                tenantId,
                TenantStaffRole.OWNER,
                TenantStaffRole.ADMIN,
                TenantStaffRole.COCINERO
        )).thenReturn(true);
        when(tenantAccessService.isMember(tenantId)).thenReturn(true);

        InventoryMovementResponse movementResponse = InventoryMovementResponse.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .stockId(stockId)
                .itemId(itemId)
                .movementType(InventoryMovementType.ENTRY)
                .reason(InventoryMovementReason.PURCHASE)
                .quantity(new BigDecimal("50.000"))
                .previousQuantity(new BigDecimal("0.000"))
                .newQuantity(new BigDecimal("50.000"))
                .reasonDetails("Compra inicial")
                .build();

        when(inventoryMovementService.register(eq(tenantId), any())).thenReturn(movementResponse);
        when(inventoryMovementService.getKardexByStock(eq(tenantId), eq(stockId), any())).thenReturn(
                PageResponse.<InventoryMovementResponse>builder()
                        .content(List.of(movementResponse))
                        .page(0)
                        .size(20)
                        .totalElements(1)
                        .totalPages(1)
                        .first(true)
                        .last(true)
                        .build()
        );

        mockMvc.perform(post("/api/v1/tenants/" + tenantId + "/inventory/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                InventoryMovementRequest.builder()
                                        .stockId(stockId)
                                        .movementType(InventoryMovementType.ENTRY)
                                        .reason(InventoryMovementReason.PURCHASE)
                                        .quantity(new BigDecimal("50.000"))
                                        .reasonDetails("Compra inicial")
                                        .build()
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.newQuantity").value(50.0));

        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/inventory/stocks/" + stockId + "/kardex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].reason").value("PURCHASE"));
    }
}
