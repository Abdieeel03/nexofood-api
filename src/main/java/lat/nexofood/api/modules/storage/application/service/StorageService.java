package lat.nexofood.api.modules.storage.application.service;

import lat.nexofood.api.modules.storage.web.dto.request.SignedUploadUrlRequest;
import lat.nexofood.api.modules.storage.web.dto.response.SignedUploadUrlResponse;

import java.util.UUID;

public interface StorageService {

    SignedUploadUrlResponse createTenantSignedUploadUrl(UUID tenantId, SignedUploadUrlRequest request);

    SignedUploadUrlResponse createOnboardingSignedUploadUrl(SignedUploadUrlRequest request);
}
