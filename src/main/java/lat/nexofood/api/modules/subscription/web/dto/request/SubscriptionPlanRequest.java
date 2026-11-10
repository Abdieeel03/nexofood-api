package lat.nexofood.api.modules.subscription.web.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
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
        @DecimalMax(value = "999999.99", message = "El precio no puede exceder 999,999.99")
        BigDecimal price,

        @NotNull(message = "El ciclo de facturación en días es obligatorio")
        @Min(value = 1, message = "El ciclo de facturación debe ser de al menos 1 día")
        @Max(value = 3650, message = "El ciclo de facturación no puede superar los 3650 días")
        Integer billingCycleDays,

        @Min(value = 1, message = "El límite de productos debe ser al menos 1")
        @Max(value = 1000000, message = "El límite de productos no puede superar 1,000,000")
        Integer maxProducts,

        Boolean isActive
) {}
