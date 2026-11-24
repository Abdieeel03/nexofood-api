package lat.nexofood.api.modules.storage.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lat.nexofood.api.common.response.ApiResponse;
import lat.nexofood.api.modules.storage.application.service.StorageService;
import lat.nexofood.api.modules.storage.web.dto.request.SignedUploadUrlRequest;
import lat.nexofood.api.modules.storage.web.dto.response.SignedUploadUrlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Storage - Supabase", description = "Generación de URLs firmadas para subida directa de imágenes a Supabase Storage")
public class TenantStorageController {

    private final StorageService storageService;

    @PostMapping("/api/v1/tenants/{tenantId}/storage/signed-upload-url")
    @PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).OWNER, T(lat.nexofood.api.modules.store.domain.TenantStaffRole).ADMIN)")
    @Operation(summary = "Generar URL firmada para subir imágenes de platos, logo o banner del restaurante (OWNER o ADMIN)")
    public ResponseEntity<ApiResponse<SignedUploadUrlResponse>> createTenantSignedUploadUrl(
            @PathVariable UUID tenantId,
            @Valid @RequestBody SignedUploadUrlRequest request
    ) {
        SignedUploadUrlResponse response = storageService.createTenantSignedUploadUrl(tenantId, request);
        return ResponseEntity.ok(
                ApiResponse.<SignedUploadUrlResponse>builder()
                        .success(true)
                        .message("URL firmada de subida generada exitosamente")
                        .data(response)
                        .build()
        );
    }

    @PostMapping("/api/v1/storage/onboarding/signed-upload-url")
    @Operation(summary = "Generar URL firmada para subir logo o banner durante el onboarding")
    public ResponseEntity<ApiResponse<SignedUploadUrlResponse>> createOnboardingSignedUploadUrl(
            @Valid @RequestBody SignedUploadUrlRequest request
    ) {
        SignedUploadUrlResponse response = storageService.createOnboardingSignedUploadUrl(request);
        return ResponseEntity.ok(
                ApiResponse.<SignedUploadUrlResponse>builder()
                        .success(true)
                        .message("URL firmada de subida generada exitosamente")
                        .data(response)
                        .build()
        );
    }
}
