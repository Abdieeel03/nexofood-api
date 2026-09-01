package lat.nexofood.api.modules.tenant.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record TenantUpdateRequest(
        @NotBlank(message = "El nombre del restaurante es obligatorio")
        @Size(max = 150)
        String name,

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
        BigDecimal defaultDeliveryFee,

        Boolean isActive
) {}
