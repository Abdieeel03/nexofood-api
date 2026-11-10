package lat.nexofood.api.modules.tracking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lat.nexofood.api.modules.identity.domain.User;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.store.domain.Tenant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "delivery_tracking",
        indexes = {
                @Index(name = "idx_delivery_tracking_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_delivery_tracking_driver_id", columnList = "driver_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DeliveryTracking {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "order_id")
    @ToString.Exclude
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    @ToString.Exclude
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    @ToString.Exclude
    private User driver;

    @Column(name = "location", columnDefinition = "geometry(Point, 4326)", nullable = false)
    private Point location;

    @Column(name = "heading")
    private Double heading;

    @Column(name = "speed_kmh")
    private Double speedKmh;

    @Column(name = "accuracy_m")
    private Double accuracyM;

    @Column(name = "remaining_distance_km", precision = 6, scale = 2)
    private BigDecimal remainingDistanceKm;

    @Column(name = "eta_minutes")
    private Integer etaMinutes;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void touchTimestamps() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.recordedAt == null) {
            this.recordedAt = now;
        }
        this.updatedAt = now;
        if (this.orderId == null && this.order != null) {
            this.orderId = this.order.getId();
        }
    }
}

