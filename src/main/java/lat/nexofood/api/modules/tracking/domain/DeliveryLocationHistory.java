package lat.nexofood.api.modules.tracking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
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
import org.hibernate.annotations.UuidGenerator;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "delivery_location_history",
        indexes = {
                @Index(name = "idx_delivery_location_history_order_recorded", columnList = "order_id, recorded_at"),
                @Index(name = "idx_delivery_location_history_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_delivery_location_history_recorded_at", columnList = "recorded_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DeliveryLocationHistory {

    @Id
    @GeneratedValue
    @UuidGenerator
    @EqualsAndHashCode.Include
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
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

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @PrePersist
    private void initRecordedAt() {
        if (this.recordedAt == null) {
            this.recordedAt = OffsetDateTime.now();
        }
    }
}

