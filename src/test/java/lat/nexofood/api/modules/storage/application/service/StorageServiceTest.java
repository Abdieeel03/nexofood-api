package lat.nexofood.api.modules.storage.application.service;

import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.storage.domain.StorageTarget;
import lat.nexofood.api.modules.storage.infrastructure.SupabaseStorageProperties;
import lat.nexofood.api.modules.storage.web.dto.request.SignedUploadUrlRequest;
import lat.nexofood.api.modules.storage.web.dto.response.SignedUploadUrlResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

    private MockRestServiceServer mockServer;
    private StorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        SupabaseStorageProperties properties = SupabaseStorageProperties.builder()
                .url("https://demo-project.supabase.co")
                .serviceRoleKey("sb_secret_test_key_123")
                .build();

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        storageService = new StorageServiceImpl(properties, currentUserProvider, builder);
    }

    @Test
    @DisplayName("createTenantSignedUploadUrl: firma URL en dish-images usando apikey sb_secret_ sin Bearer")
    void createsSignedUploadUrlForDishImageWithSecretKey() {
        UUID tenantId = UUID.randomUUID();
        String expectedPathPrefix = "tenants/" + tenantId + "/dishes/";

        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://demo-project.supabase.co/storage/v1/object/upload/sign/dish-images/" + expectedPathPrefix)))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "sb_secret_test_key_123"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess(
                        "{\"url\":\"/object/upload/sign/dish-images/" + expectedPathPrefix + "sample.webp?token=tok123\",\"token\":\"tok123\"}",
                        MediaType.APPLICATION_JSON
                ));

        SignedUploadUrlResponse resp = storageService.createTenantSignedUploadUrl(
                tenantId,
                new SignedUploadUrlRequest(StorageTarget.DISH_IMAGE, "image/webp", 1024L)
        );

        assertEquals("dish-images", resp.bucket());
        assertTrue(resp.path().startsWith(expectedPathPrefix));
        assertTrue(resp.path().endsWith(".webp"));
        assertTrue(resp.signedUploadUrl().startsWith("https://demo-project.supabase.co/storage/v1/object/upload/sign/dish-images/"));
        assertEquals("https://demo-project.supabase.co/storage/v1/object/public/dish-images/" + resp.path(), resp.publicUrl());
        assertEquals("tok123", resp.token());
        mockServer.verify();
    }

    @Test
    @DisplayName("createOnboardingSignedUploadUrl: permite logo/banner y rechaza DISH_IMAGE o archivos inválidos")
    void handlesOnboardingAndValidation() {
        UUID userId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);

        assertThrows(BadRequestException.class, () -> storageService.createOnboardingSignedUploadUrl(
                new SignedUploadUrlRequest(StorageTarget.DISH_IMAGE, "image/png", 500L)
        ));

        assertThrows(BadRequestException.class, () -> storageService.createTenantSignedUploadUrl(
                UUID.randomUUID(),
                new SignedUploadUrlRequest(StorageTarget.DISH_IMAGE, "image/svg+xml", 500L)
        ));

        assertThrows(BadRequestException.class, () -> storageService.createTenantSignedUploadUrl(
                UUID.randomUUID(),
                new SignedUploadUrlRequest(StorageTarget.TENANT_LOGO, "image/png", 6_000_000L)
        ));

        mockServer.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://demo-project.supabase.co/storage/v1/object/upload/sign/tenant-assets/onboarding/" + userId + "/logo/")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"url\":\"/object/upload/sign/tenant-assets/onboarding/" + userId + "/logo/a.svg?token=svgtok\",\"token\":\"svgtok\"}",
                        MediaType.APPLICATION_JSON
                ));

        SignedUploadUrlResponse resp = storageService.createOnboardingSignedUploadUrl(
                new SignedUploadUrlRequest(StorageTarget.TENANT_LOGO, "image/svg+xml", 2048L)
        );
        assertEquals("tenant-assets", resp.bucket());
        assertTrue(resp.path().endsWith(".svg"));
        mockServer.verify();
    }
}
