package lat.nexofood.api.modules.storage.web.dto.response;

import lombok.Builder;

@Builder
public record SignedUploadUrlResponse(
        String signedUploadUrl,
        String publicUrl,
        String bucket,
        String path,
        String token,
        long expiresInSeconds
) {
}
