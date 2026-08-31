package lat.nexofood.api.modules.tenant.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record TenantResponse(
        UUID id,
        UUID subscriptionId,
        UUID ownerId,
        String name,
        String slug,
        String logoUrl,
        String bannerUrl,
        String phone,
        String address,
        Double latitude,
        Double longitude,
        BigDecimal deliveryRadiusKm,
        BigDecimal defaultDeliveryFee,
        Boolean isMpConnected,
        String mpUserId,
        OffsetDateTime mpConnectedAt,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
