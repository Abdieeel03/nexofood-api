package lat.nexofood.api.modules.catalog.web.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record TaxRequest(
        @NotBlank(message = "El nombre del impuesto es obligatorio")
        @Size(max = 50, message = "El nombre del impuesto no debe superar los 50 caracteres")
        String name,

        @NotNull(message = "La tasa del impuesto es obligatoria")
        @DecimalMin(value = "0.00", message = "La tasa del impuesto no puede ser negativa")
        @Digits(integer = 3, fraction = 2, message = "La tasa debe tener como máximo 3 enteros y 2 decimales")
        BigDecimal rate,

        @Size(max = 20, message = "El código tributario no debe superar los 20 caracteres")
        String code,

        Boolean isInclusive,

        Boolean isActive
) {}
