package lat.nexofood.api.modules.storage.web.controller;

import jakarta.servlet.ServletException;
import lat.nexofood.api.common.exception.GlobalExceptionHandler;
import lat.nexofood.api.common.security.TenantAccessService;
import lat.nexofood.api.modules.storage.application.service.StorageService;
import lat.nexofood.api.modules.storage.web.dto.request.SignedUploadUrlRequest;
import lat.nexofood.api.modules.storage.web.dto.response.SignedUploadUrlResponse;
import lat.nexofood.api.modules.store.domain.TenantStaffRole;
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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
class TenantStorageControllerTest {

    private StorageService storageService;
    private TenantAccessService tenantAccess;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        tenantAccess = mock(TenantAccessService.class);

        StaticApplicationContext appContext = new StaticApplicationContext();
        appContext.getBeanFactory().registerSingleton("tenantAccess", tenantAccess);
        appContext.refresh();

        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setApplicationContext(appContext);

        PreAuthorizeAuthorizationManager authorizationManager = new PreAuthorizeAuthorizationManager();
        authorizationManager.setExpressionHandler(expressionHandler);

        AuthorizationManagerBeforeMethodInterceptor interceptor =
                AuthorizationManagerBeforeMethodInterceptor.preAuthorize(authorizationManager);

        AspectJProxyFactory factory = new AspectJProxyFactory(new TenantStorageController(storageService));
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        TenantStorageController proxiedController = (TenantStorageController) factory.getProxy();

        mockMvc = MockMvcBuilders.standaloneSetup(proxiedController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /tenants/{tenantId}/storage/signed-upload-url: permite OWNER/ADMIN y rechaza otros roles")
    void authorizesTenantSignedUploadUrl() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(tenantAccess.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(true);
        when(storageService.createTenantSignedUploadUrl(eq(tenantId), any(SignedUploadUrlRequest.class)))
                .thenReturn(SignedUploadUrlResponse.builder()
                        .signedUploadUrl("https://demo.supabase.co/storage/v1/object/upload/sign/dish-images/a.png?token=t")
                        .publicUrl("https://demo.supabase.co/storage/v1/object/public/dish-images/a.png")
                        .bucket("dish-images")
                        .path("a.png")
                        .token("t")
                        .expiresInSeconds(7200)
                        .build());

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/storage/signed-upload-url", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"target":"DISH_IMAGE","contentType":"image/png","fileSizeBytes":1024}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bucket").value("dish-images"));

        when(tenantAccess.hasAnyRole(tenantId, TenantStaffRole.OWNER, TenantStaffRole.ADMIN))
                .thenReturn(false);

        ServletException exception = assertThrows(ServletException.class, () ->
                mockMvc.perform(post("/api/v1/tenants/{tenantId}/storage/signed-upload-url", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"target":"DISH_IMAGE","contentType":"image/png","fileSizeBytes":1024}
                                """)));
        assertInstanceOf(AccessDeniedException.class, exception.getCause());
    }
}
