package lat.nexofood.api.modules.store.web.controller;

import lat.nexofood.api.common.exception.GlobalExceptionHandler;
import lat.nexofood.api.common.response.PageResponse;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.modules.store.application.service.tenant.TenantService;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
class AdminTenantControllerTest {

    private TenantService tenantService;
    private TenantAccessService tenantAccessService;
    private AdminTenantController proxiedController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tenantService = mock(TenantService.class);
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

        AspectJProxyFactory factory = new AspectJProxyFactory(new AdminTenantController(tenantService));
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        proxiedController = (AdminTenantController) factory.getProxy();

        mockMvc = MockMvcBuilders.standaloneSetup(proxiedController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/admin/tenants deniega acceso si el usuario no es SUPERADMIN")
    void getAllTenantsDeniedForRegularUser() {
        when(tenantAccessService.isSuperAdmin()).thenReturn(false);

        assertThrows(AccessDeniedException.class, () ->
                proxiedController.getAllTenants(null, null, null, PageRequest.of(0, 20))
        );
        verify(tenantService, never()).getAllTenants(any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/v1/admin/tenants permite a SUPERADMIN listar tiendas paginadas con filtros")
    void getAllTenantsAllowedForSuperAdminOnTenantsPath() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(tenantAccessService.isSuperAdmin()).thenReturn(true);

        TenantResponse tenantResponse = TenantResponse.builder()
                .id(tenantId)
                .name("Polleria Nexo")
                .slug("polleria-nexo")
                .isActive(true)
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(22, 0))
                .isOpen(true)
                .build();

        PageResponse<TenantResponse> pageResponse = PageResponse.<TenantResponse>builder()
                .content(List.of(tenantResponse))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(tenantService.getAllTenants(eq(true), eq(true), eq("nexo"), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/tenants")
                        .param("isActive", "true")
                        .param("isOpen", "true")
                        .param("search", "nexo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].slug").value("polleria-nexo"))
                .andExpect(jsonPath("$.data.content[0].isOpen").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/admin/stores funciona como alias para SUPERADMIN")
    void getAllTenantsAllowedForSuperAdminOnStoresAlias() throws Exception {
        when(tenantAccessService.isSuperAdmin()).thenReturn(true);
        PageResponse<TenantResponse> emptyPage = PageResponse.<TenantResponse>builder()
                .content(List.of())
                .page(0)
                .size(20)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .build();

        when(tenantService.getAllTenants(eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/admin/stores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }
}
