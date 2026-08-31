package lat.nexofood.api.modules.subscription.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record SubscriptionPlanRequest(
        @NotBlank(message = "El nombre del plan es obligatorio")
        @Size(max = 100)
        String name,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
        BigDecimal price,

        @NotNull(message = "El ciclo de facturación en días es obligatorio")
        @Min(value = 1, message = "El ciclo de facturación debe ser de al menos 1 día")
        Integer billingCycleDays,

        @Min(value = 1, message = "El límite de productos debe ser al menos 1")
        Integer maxProducts,

        Boolean isActive
) {}
