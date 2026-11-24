package lat.nexofood.api.modules.storage.application.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.security.CurrentUserProvider;
import lat.nexofood.api.modules.storage.domain.StorageTarget;
import lat.nexofood.api.modules.storage.infrastructure.SupabaseStorageProperties;
import lat.nexofood.api.modules.storage.web.dto.request.SignedUploadUrlRequest;
import lat.nexofood.api.modules.storage.web.dto.response.SignedUploadUrlResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class StorageServiceImpl implements StorageService {

    private static final long SIGNED_UPLOAD_TTL_SECONDS = 7_200L;
    private static final String HEADER_APIKEY = "apikey";
    private static final String SECRET_KEY_PREFIX = "sb_secret_";

    private final SupabaseStorageProperties properties;
    private final CurrentUserProvider currentUserProvider;
    private final RestClient restClient;

    @Autowired
    public StorageServiceImpl(
            SupabaseStorageProperties properties,
            CurrentUserProvider currentUserProvider
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.properties = properties;
        this.currentUserProvider = currentUserProvider;
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public StorageServiceImpl(
            SupabaseStorageProperties properties,
            CurrentUserProvider currentUserProvider,
            RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.currentUserProvider = currentUserProvider;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public SignedUploadUrlResponse createTenantSignedUploadUrl(UUID tenantId, SignedUploadUrlRequest request) {
        if (tenantId == null) {
            throw new BadRequestException("El identificador del restaurante es obligatorio");
        }
        return generateSignedUploadUrl("tenants/" + tenantId, request);
    }

    @Override
    public SignedUploadUrlResponse createOnboardingSignedUploadUrl(SignedUploadUrlRequest request) {
        if (request.target() == StorageTarget.DISH_IMAGE) {
            throw new BadRequestException("Las imágenes de productos requieren un restaurante creado");
        }
        UUID userId = currentUserProvider.getCurrentUserId();
        return generateSignedUploadUrl("onboarding/" + userId, request);
    }

    private SignedUploadUrlResponse generateSignedUploadUrl(String scopePrefix, SignedUploadUrlRequest request) {
        String baseUrl = normalizeBaseUrl(properties.getUrl());
        String serviceKey = properties.getServiceRoleKey() != null ? properties.getServiceRoleKey().trim() : "";
        if (baseUrl.isBlank() || serviceKey.isBlank()) {
            throw new IllegalStateException("Supabase Storage no está configurado en el servidor (SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY)");
        }

        StorageTarget target = request.target();
        String normalizedMime = normalizeMimeType(request.contentType());
        if (!target.isAllowedMimeType(normalizedMime)) {
            throw new BadRequestException("Formato de archivo no permitido para " + target.name() + ": " + request.contentType());
        }

        long maxBytes = properties.getStorage().getMaxFileSizeBytes() > 0
                ? properties.getStorage().getMaxFileSizeBytes()
                : 5_242_880L;
        if (request.fileSizeBytes() == null || request.fileSizeBytes() <= 0 || request.fileSizeBytes() > maxBytes) {
            throw new BadRequestException("El archivo excede el tamaño máximo permitido de " + (maxBytes / (1024 * 1024)) + " MB");
        }

        String bucket = resolveBucket(target);
        String ext = target.extensionFor(normalizedMime);
        String objectPath = scopePrefix + "/" + target.getFolder() + "/" + UUID.randomUUID() + "." + ext;

        String signEndpoint = baseUrl + "/storage/v1/object/upload/sign/" + bucket + "/" + objectPath;

        SupabaseSignedUploadResponse signResp;
        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(signEndpoint)
                    .header(HEADER_APIKEY, serviceKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);

            if (!serviceKey.startsWith(SECRET_KEY_PREFIX)) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceKey);
            }

            signResp = spec.body(Map.of())
                    .retrieve()
                    .body(SupabaseSignedUploadResponse.class);
        } catch (RestClientResponseException ex) {
            log.warn("Supabase Storage respondió HTTP {} al firmar subida en bucket={}", ex.getStatusCode().value(), bucket);
            throw new IllegalStateException("No se pudo generar la URL de subida en Supabase Storage");
        } catch (RestClientException ex) {
            throw new IllegalStateException("Error de conexión al generar la URL de subida en Supabase Storage", ex);
        }

        if (signResp == null || signResp.url() == null || signResp.url().isBlank()) {
            throw new IllegalStateException("Respuesta inválida de Supabase Storage al firmar la subida");
        }

        String signedRelative = signResp.url().startsWith("/") ? signResp.url() : "/" + signResp.url();
        String signedUploadUrl = baseUrl + "/storage/v1" + signedRelative;
        String publicUrl = baseUrl + "/storage/v1/object/public/" + bucket + "/" + objectPath;

        return SignedUploadUrlResponse.builder()
                .signedUploadUrl(signedUploadUrl)
                .publicUrl(publicUrl)
                .bucket(bucket)
                .path(objectPath)
                .token(signResp.token())
                .expiresInSeconds(SIGNED_UPLOAD_TTL_SECONDS)
                .build();
    }

    private String resolveBucket(StorageTarget target) {
        return switch (target) {
            case DISH_IMAGE -> properties.getStorage().getDishImagesBucket();
            case TENANT_LOGO, TENANT_BANNER -> properties.getStorage().getTenantAssetsBucket();
        };
    }

    private String normalizeMimeType(String rawMime) {
        if (rawMime == null) {
            return "";
        }
        String base = rawMime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(base)) {
            return "image/jpeg";
        }
        return base;
    }

    private String normalizeBaseUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        String trimmed = rawUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SupabaseSignedUploadResponse(
            @JsonProperty("url") String url,
            @JsonProperty("token") String token
    ) {
    }
}
