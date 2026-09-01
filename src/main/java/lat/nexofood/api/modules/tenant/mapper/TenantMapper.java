package lat.nexofood.api.modules.tenant.mapper;

import lat.nexofood.api.common.util.GeoUtils;
import lat.nexofood.api.modules.auth.domain.User;
import lat.nexofood.api.modules.subscription.domain.Subscription;
import lat.nexofood.api.modules.tenant.domain.Tenant;
import lat.nexofood.api.modules.tenant.dto.request.TenantCreateRequest;
import lat.nexofood.api.modules.tenant.dto.response.TenantResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TenantMapper {

    public TenantResponse toResponse(Tenant tenant) {
        if (tenant == null) {
            return null;
        }
        return TenantResponse.builder()
                .id(tenant.getId())
                .subscriptionId(tenant.getSubscription() != null ? tenant.getSubscription().getId() : null)
                .ownerId(tenant.getOwner() != null ? tenant.getOwner().getId() : null)
                .name(tenant.getName())
                .slug(tenant.getSlug())
                .logoUrl(tenant.getLogoUrl())
                .bannerUrl(tenant.getBannerUrl())
                .phone(tenant.getPhone())
                .address(tenant.getAddress())
                .latitude(GeoUtils.getLatitude(tenant.getLocation()))
                .longitude(GeoUtils.getLongitude(tenant.getLocation()))
                .deliveryRadiusKm(tenant.getDeliveryRadiusKm())
                .defaultDeliveryFee(tenant.getDefaultDeliveryFee())
                .isMpConnected(tenant.getMpConnectedAt() != null)
                .mpUserId(tenant.getMpUserId())
                .mpConnectedAt(tenant.getMpConnectedAt())
                .isActive(tenant.getIsActive())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }

    public Tenant toEntity(TenantCreateRequest request, Subscription subscription, User owner) {
        if (request == null) {
            return null;
        }
        return Tenant.builder()
                .subscription(subscription)
                .owner(owner)
                .name(request.name())
                .slug(request.slug())
                .logoUrl(request.logoUrl())
                .bannerUrl(request.bannerUrl())
                .phone(request.phone())
                .address(request.address())
                .location(GeoUtils.createPoint(request.latitude(), request.longitude()))
                .deliveryRadiusKm(request.deliveryRadiusKm() != null ? request.deliveryRadiusKm() : new BigDecimal("5.00"))
                .defaultDeliveryFee(request.defaultDeliveryFee() != null ? request.defaultDeliveryFee() : BigDecimal.ZERO)
                .build();
    }
}
