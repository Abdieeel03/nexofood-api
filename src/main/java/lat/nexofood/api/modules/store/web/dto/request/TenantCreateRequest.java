package lat.nexofood.api.modules.store.web.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record TenantCreateRequest(
        UUID subscriptionId,

        @NotBlank(message = "El nombre del restaurante es obligatorio")
        @Size(max = 150)
        String name,

        @NotBlank(message = "El slug es obligatorio")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "El slug solo puede contener letras minúsculas, números y guiones")
        @Size(max = 100)
        String slug,

        @Size(max = 500, message = "El logo URL no puede superar los 500 caracteres")
        String logoUrl,

        @Size(max = 500, message = "El banner URL no puede superar los 500 caracteres")
        String bannerUrl,

        @Size(max = 20)
        String phone,

        @Size(max = 255, message = "La dirección no puede superar los 255 caracteres")
        String address,

        @DecimalMin(value = "-90.0", message = "La latitud debe estar entre -90.0 y 90.0")
        @DecimalMax(value = "90.0", message = "La latitud debe estar entre -90.0 y 90.0")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "La longitud debe estar entre -180.0 y 180.0")
        @DecimalMax(value = "180.0", message = "La longitud debe estar entre -180.0 y 180.0")
        Double longitude,

        @DecimalMin(value = "0.00")
        @DecimalMax(value = "100.00", message = "El radio de entrega no puede superar los 100 km")
        BigDecimal deliveryRadiusKm,

        @DecimalMin(value = "0.00")
        @DecimalMax(value = "1000.00", message = "El costo de envío no puede superar 1000")
        BigDecimal defaultDeliveryFee
) {}
