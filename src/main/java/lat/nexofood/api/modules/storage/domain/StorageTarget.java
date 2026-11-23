package lat.nexofood.api.modules.storage.domain;

import java.util.Map;
import java.util.Set;

/**
 * Destinos permitidos para la generación de URLs firmadas de subida en Supabase Storage.
 */
public enum StorageTarget {
    DISH_IMAGE("dishes", Set.of("image/png", "image/jpeg", "image/webp")),
    TENANT_LOGO("logo", Set.of("image/png", "image/jpeg", "image/webp", "image/svg+xml")),
    TENANT_BANNER("banner", Set.of("image/png", "image/jpeg", "image/webp", "image/svg+xml"));

    private static final Map<String, String> MIME_TO_EXT = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp",
            "image/svg+xml", "svg"
    );

    private final String folder;
    private final Set<String> allowedMimeTypes;

    StorageTarget(String folder, Set<String> allowedMimeTypes) {
        this.folder = folder;
        this.allowedMimeTypes = allowedMimeTypes;
    }

    public String getFolder() {
        return folder;
    }

    public boolean isAllowedMimeType(String normalizedMimeType) {
        return normalizedMimeType != null && allowedMimeTypes.contains(normalizedMimeType);
    }

    public String extensionFor(String normalizedMimeType) {
        return MIME_TO_EXT.get(normalizedMimeType);
    }
}
