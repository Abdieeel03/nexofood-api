package lat.nexofood.api.modules.store.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lat.nexofood.api.common.exception.GlobalExceptionHandler;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.modules.store.application.service.customer.TenantCustomerService;
import lat.nexofood.api.modules.store.application.service.tenant.TenantService;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
import lat.nexofood.api.modules.store.web.dto.request.CustomerBlockRequest;
import lat.nexofood.api.modules.store.web.dto.request.MemberRoleUpdateRequest;
import lat.nexofood.api.modules.store.web.dto.request.TenantStatusRequest;
import lat.nexofood.api.modules.store.web.dto.request.TenantUpdateRequest;
import lat.nexofood.api.modules.store.web.dto.response.TenantCustomerResponse;
import lat.nexofood.api.modules.store.web.dto.response.TenantMemberResponse;
import lat.nexofood.api.modules.store.web.dto.response.TenantResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
class TenantAuthorizationControllerTest {

    private TenantService tenantService;
    private TenantCustomerService tenantCustomerService;
    private TenantAccessService tenantAccessService;

    private TenantController proxiedTenantController;
    private TenantMemberController proxiedMemberController;
    private TenantCustomerController proxiedCustomerController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tenantService = mock(TenantService.class);
        tenantCustomerService = mock(TenantCustomerService.class);
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

        proxiedTenantController = createSecuredProxy(new TenantController(tenantService), interceptor);
        proxiedMemberController = createSecuredProxy(new TenantMemberController(tenantService), interceptor);
        proxiedCustomerController = createSecuredProxy(new TenantCustomerController(tenantCustomerService), interceptor);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        proxiedTenantController,
                        proxiedMemberController,
                        proxiedCustomerController
                )
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
    @DisplayName("PUT /api/v1/tenants/{id} lanza AccessDeniedException si el usuario no es OWNER")
    void updateTenantDeniedWhenNotOwner() {
        UUID tenantId = UUID.randomUUID();
        when(tenantAccessService.isOwner(tenantId)).thenReturn(false);

        TenantUpdateRequest request = TenantUpdateRequest.builder().name("Nuevo Nombre").build();

        assertThrows(AccessDeniedException.class, () ->
                proxiedTenantController.updateTenant(tenantId, request)
        );
        verify(tenantService, never()).updateTenant(any(), any());
    }

    @Test
    @DisplayName("PUT /api/v1/tenants/{id} retorna 200 cuando el usuario es OWNER")
    void updateTenantAllowedWhenOwner() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(tenantAccessService.isOwner(tenantId)).thenReturn(true);
        when(tenantService.updateTenant(eq(tenantId), any())).thenReturn(
                TenantResponse.builder().id(tenantId).name("Nuevo Nombre").build()
        );

        TenantUpdateRequest request = TenantUpdateRequest.builder().name("Nuevo Nombre").build();

        mockMvc.perform(put("/api/v1/tenants/" + tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Nuevo Nombre"));
    }

    @Test
    @DisplayName("PATCH /api/v1/tenants/{id}/status valida DTO y retorna 400 cuando isActive es nulo")
    void changeStatusReturns400WhenIsActiveNull() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(tenantAccessService.isOwner(tenantId)).thenReturn(true);

        mockMvc.perform(patch("/api/v1/tenants/" + tenantId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("PATCH /api/v1/tenants/{tenantId}/members/{memberId}/role deniega acceso si no es OWNER ni ADMIN")
    void updateMemberRoleDeniedWhenUnauthorized() {
        UUID tenantId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(false);

        MemberRoleUpdateRequest request = new MemberRoleUpdateRequest(TenantStaffRole.COCINERO);

        assertThrows(AccessDeniedException.class, () ->
                proxiedMemberController.updateMemberRole(tenantId, memberId, request)
        );
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/tenants/{tenantId}/customers/{customerId}/block permite a OWNER/ADMIN con DTO validado")
    void blockCustomerAllowedForAdmin() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(tenantAccessService.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(true);
        when(tenantCustomerService.blockCustomer(tenantId, customerId, true))
                .thenReturn(TenantCustomerResponse.builder().id(customerId).isBlocked(true).build());

        CustomerBlockRequest request = new CustomerBlockRequest(true);

        mockMvc.perform(patch("/api/v1/admin/tenants/" + tenantId + "/customers/" + customerId + "/block")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isBlocked").value(true));
    }
}
