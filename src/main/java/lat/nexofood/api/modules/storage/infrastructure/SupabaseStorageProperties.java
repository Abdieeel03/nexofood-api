package lat.nexofood.api.modules.storage.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "supabase")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupabaseStorageProperties {

    private String url;

    private String serviceRoleKey;

    @Builder.Default
    private Storage storage = new Storage();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Storage {
        @Builder.Default
        private String dishImagesBucket = "dish-images";

        @Builder.Default
        private String tenantAssetsBucket = "tenant-assets";

        @Builder.Default
        private long maxFileSizeBytes = 5_242_880L;
    }
}
