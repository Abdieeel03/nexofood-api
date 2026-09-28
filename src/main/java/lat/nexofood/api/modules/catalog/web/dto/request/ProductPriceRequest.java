package lat.nexofood.api.modules.catalog.web.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Builder
public record ProductPriceRequest(
        UUID id,

        @NotBlank(message = "El nombre del precio o promoción es obligatorio")
        @Size(max = 100, message = "El nombre no puede exceder los 100 caracteres")
        String name,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
        BigDecimal price,

        Boolean isBase,

        @DecimalMin(value = "0.00", message = "El porcentaje de descuento no puede ser negativo")
        BigDecimal discountPercentage,

        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,

        @Size(max = 100, message = "Los días de la semana no pueden exceder los 100 caracteres")
        String daysOfWeek,

        Integer priority,
        Boolean isActive
) {}
