package lat.nexofood.api.modules.store.web.dto.response;

import lat.nexofood.api.modules.store.domain.StorefrontStyle;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalTime;
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
        StorefrontStyle storefrontStyle,
        String phone,
        String address,
        Double latitude,
        Double longitude,
        BigDecimal deliveryRadiusKm,
        BigDecimal defaultDeliveryFee,
        Boolean isMpConnected,
        Boolean onlinePaymentsEnabled,
        String mpUserId,
        OffsetDateTime mpConnectedAt,
        OffsetDateTime mpTokenExpiresAt,
        Boolean isActive,
        LocalTime openingTime,
        LocalTime closingTime,
        Boolean isOpen,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
