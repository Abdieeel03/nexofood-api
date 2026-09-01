package lat.nexofood.api.modules.tenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lat.nexofood.api.common.model.BaseEntity;
import lat.nexofood.api.modules.auth.domain.User;
import lat.nexofood.api.modules.subscription.domain.Subscription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.UuidGenerator;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Tenant extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @EqualsAndHashCode.Include
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", unique = true, nullable = false)
    @ToString.Exclude
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    @ToString.Exclude
    private User owner;

    @Column(name = "name", length = 150, nullable = false)
    private String name;

    @Column(name = "slug", unique = true, length = 100, nullable = false)
    private String slug;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "banner_url", columnDefinition = "TEXT")
    private String bannerUrl;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "location", columnDefinition = "geometry(Point, 4326)")
    private Point location;

    @Column(name = "delivery_radius_km", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal deliveryRadiusKm = new BigDecimal("5.00");

    @Column(name = "default_delivery_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal defaultDeliveryFee = new BigDecimal("0.00");

    @Column(name = "mp_access_token", columnDefinition = "TEXT")
    @ToString.Exclude
    private String mpAccessToken;

    @Column(name = "mp_public_key", columnDefinition = "TEXT")
    @ToString.Exclude
    private String mpPublicKey;

    @Column(name = "mp_refresh_token", columnDefinition = "TEXT")
    @ToString.Exclude
    private String mpRefreshToken;

    @Column(name = "mp_user_id", length = 100)
    private String mpUserId;

    @Column(name = "mp_connected_at")
    private OffsetDateTime mpConnectedAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
