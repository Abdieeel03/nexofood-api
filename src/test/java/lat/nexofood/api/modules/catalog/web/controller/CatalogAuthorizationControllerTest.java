package lat.nexofood.api.modules.catalog.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lat.nexofood.api.common.exception.GlobalExceptionHandler;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.modules.catalog.application.service.CategoryService;
import lat.nexofood.api.modules.catalog.application.service.ProductPriceService;
import lat.nexofood.api.modules.catalog.application.service.ProductService;
import lat.nexofood.api.modules.catalog.application.service.TaxService;
import lat.nexofood.api.modules.catalog.web.dto.request.CategoryRequest;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductPriceRequest;
import lat.nexofood.api.modules.catalog.web.dto.request.ProductRequest;
import lat.nexofood.api.modules.catalog.web.dto.request.TaxRequest;
import lat.nexofood.api.modules.catalog.web.dto.response.CategoryResponse;
import lat.nexofood.api.modules.catalog.web.dto.response.ProductPriceResponse;
import lat.nexofood.api.modules.catalog.web.dto.response.ProductResponse;
import lat.nexofood.api.modules.catalog.web.dto.response.TaxResponse;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.data.domain.PageRequest;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
class CatalogAuthorizationControllerTest {

    private TaxService taxService;
    private CategoryService categoryService;
    private ProductService productService;
    private ProductPriceService productPriceService;
    private TenantAccessService tenantAccessService;

    private TaxController proxiedTaxController;
    private CategoryController proxiedCategoryController;
    private ProductController proxiedProductController;
    private ProductPriceController proxiedProductPriceController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        taxService = mock(TaxService.class);
        categoryService = mock(CategoryService.class);
        productService = mock(ProductService.class);
        productPriceService = mock(ProductPriceService.class);
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

        proxiedTaxController = createSecuredProxy(new TaxController(taxService), interceptor);
        proxiedCategoryController = createSecuredProxy(new CategoryController(categoryService), interceptor);
        proxiedProductController = createSecuredProxy(new ProductController(productService), interceptor);
        proxiedProductPriceController = createSecuredProxy(new ProductPriceController(productPriceService), interceptor);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        proxiedTaxController,
                        proxiedCategoryController,
                        proxiedProductController,
                        proxiedProductPriceController
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
    @DisplayName("Backoffice GET: permite acceso a cualquier miembro del staff (OWNER, ADMIN, COCINERO, REPARTIDOR) via isMember")
    void backofficeGetAllowedForAnyStaffMember() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        // Un COCINERO o REPARTIDOR es miembro (isMember = true), aunque no sea OWNER/ADMIN
        when(tenantAccessService.isMember(tenantId)).thenReturn(true);
        when(taxService.findAllByTenant(tenantId)).thenReturn(List.of());
        when(categoryService.findAllByTenant(tenantId)).thenReturn(List.of());
        when(productService.findByIdAndTenant(tenantId, productId)).thenReturn(
                ProductResponse.builder().id(productId).name("Hamburguesa Clásica").build()
        );
        when(productPriceService.findAllByProduct(tenantId, productId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/taxes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Hamburguesa Clásica"));

        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/products/" + productId + "/prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Si no es miembro del tenant, GET es denegado
        UUID otherTenantId = UUID.randomUUID();
        when(tenantAccessService.isMember(otherTenantId)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () ->
                proxiedProductController.getProducts(otherTenantId, null, null, PageRequest.of(0, 20))
        );
    }

    @Test
    @DisplayName("TaxController POST: deniega acceso cuando el usuario no tiene rol OWNER ni ADMIN en el tenant")
    void taxControllerDeniedWhenNotOwnerOrAdmin() {
        UUID tenantId = UUID.randomUUID();
        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(false);

        TaxRequest request = TaxRequest.builder().name("IGV").rate(new BigDecimal("18.00")).build();

        assertThrows(AccessDeniedException.class, () ->
                proxiedTaxController.createTax(tenantId, request)
        );
        verify(taxService, never()).create(any(), any());
    }

    @Test
    @DisplayName("TaxController POST: permite crear impuesto (201) cuando el usuario es OWNER o ADMIN")
    void taxControllerAllowedForOwnerOrAdmin() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(true);
        when(taxService.create(eq(tenantId), any())).thenReturn(
                TaxResponse.builder().id(UUID.randomUUID()).tenantId(tenantId).name("IGV 18%").rate(new BigDecimal("18.00")).build()
        );

        TaxRequest request = TaxRequest.builder().name("IGV 18%").rate(new BigDecimal("18.00")).build();

        mockMvc.perform(post("/api/v1/tenants/" + tenantId + "/taxes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("IGV 18%"));
    }

    @Test
    @DisplayName("CategoryController POST: deniega acceso a usuarios sin rol OWNER/ADMIN y permite a ADMIN")
    void categoryControllerEnforcesAuthorization() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(false);

        CategoryRequest request = CategoryRequest.builder().name("Bebidas").build();
        assertThrows(AccessDeniedException.class, () ->
                proxiedCategoryController.createCategory(tenantId, request)
        );

        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(true);
        when(categoryService.create(eq(tenantId), any())).thenReturn(
                CategoryResponse.builder().id(UUID.randomUUID()).tenantId(tenantId).name("Bebidas").build()
        );

        mockMvc.perform(post("/api/v1/tenants/" + tenantId + "/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Bebidas"));
    }

    @Test
    @DisplayName("ProductController: deniega mutaciones a no autorizados y valida DTO en PATCH availability")
    void productControllerAuthorizationAndValidation() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(false);

        ProductRequest request = ProductRequest.builder().name("Pizza").price(new BigDecimal("30.00")).build();
        assertThrows(AccessDeniedException.class, () ->
                proxiedProductController.createProduct(tenantId, request)
        );

        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(true);

        mockMvc.perform(patch("/api/v1/tenants/" + tenantId + "/products/" + productId + "/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ProductPriceController POST: deniega acceso si no es OWNER/ADMIN y permite crear precio cuando sí lo es")
    void productPriceControllerEnforcesAuthorization() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(false);

        ProductPriceRequest request = ProductPriceRequest.builder()
                .name("Happy Hour")
                .price(new BigDecimal("15.00"))
                .build();

        assertThrows(AccessDeniedException.class, () ->
                proxiedProductPriceController.createPrice(tenantId, productId, request)
        );

        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(true);
        when(productPriceService.create(eq(tenantId), eq(productId), any())).thenReturn(
                ProductPriceResponse.builder().id(UUID.randomUUID()).productId(productId).name("Happy Hour").price(new BigDecimal("15.00")).build()
        );

        mockMvc.perform(post("/api/v1/tenants/" + tenantId + "/products/" + productId + "/prices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Happy Hour"));
    }
}
