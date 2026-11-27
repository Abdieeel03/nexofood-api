package lat.nexofood.api.modules.analytics.web.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lat.nexofood.api.modules.analytics.domain.ExpenseCategory;
import lat.nexofood.api.modules.analytics.domain.ExpenseType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record TenantExpenseRequest(
        @NotNull(message = "El tipo de gasto es obligatorio")
        ExpenseType type,

        @NotNull(message = "La categoría del gasto es obligatoria")
        ExpenseCategory category,

        @NotBlank(message = "La descripción del gasto es obligatoria")
        @Size(max = 255, message = "La descripción no puede exceder los 255 caracteres")
        String description,

        @NotNull(message = "El monto del gasto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
        @DecimalMax(value = "99999999.99", message = "El monto excede el máximo permitido")
        BigDecimal amount,

        @NotNull(message = "La fecha del gasto es obligatoria")
        LocalDate expenseDate,

        @Size(max = 1000, message = "Las notas no pueden exceder los 1000 caracteres")
        String notes
) {}
