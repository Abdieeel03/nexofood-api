package lat.nexofood.api.modules.store.web.dto.request;

import jakarta.validation.constraints.NotNull;
import lat.nexofood.api.modules.store.domain.StorefrontStyle;
import lombok.Builder;

@Builder
public record TenantStyleUpdateRequest(
        @NotNull(message = "El estilo del storefront es obligatorio")
        StorefrontStyle storefrontStyle
) {}
