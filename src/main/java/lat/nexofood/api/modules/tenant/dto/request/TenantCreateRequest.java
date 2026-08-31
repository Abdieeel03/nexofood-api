package lat.nexofood.api.modules.tenant.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record TenantCreateRequest(
        @NotNull(message = "El ID de la suscripción es obligatorio")
        UUID subscriptionId,

        @NotNull(message = "El ID del propietario es obligatorio")
        UUID ownerId,

        @NotBlank(message = "El nombre del restaurante es obligatorio")
        @Size(max = 150)
        String name,

        @NotBlank(message = "El slug es obligatorio")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "El slug solo puede contener letras minúsculas, números y guiones")
        @Size(max = 100)
        String slug,

        String logoUrl,
        String bannerUrl,

        @Size(max = 20)
        String phone,

        String address,
        Double latitude,
        Double longitude,

        @DecimalMin(value = "0.00")
        BigDecimal deliveryRadiusKm,

        @DecimalMin(value = "0.00")
        BigDecimal defaultDeliveryFee
) {}
