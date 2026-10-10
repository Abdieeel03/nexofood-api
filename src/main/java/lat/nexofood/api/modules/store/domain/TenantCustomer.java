package lat.nexofood.api.modules.store.domain;

import jakarta.persistence.*;
import lat.nexofood.api.common.model.BaseEntity;
import lat.nexofood.api.modules.identity.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;

@Entity
@Table(name = "tenant_customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class TenantCustomer extends BaseEntity {

    @EmbeddedId
    @EqualsAndHashCode.Include
    @Builder.Default
    private TenantCustomerId id = new TenantCustomerId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tenantId")
    @JoinColumn(name = "tenant_id", nullable = false)
    @ToString.Exclude
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @Column(name = "loyalty_points", nullable = false)
    @Builder.Default
    private Integer loyaltyPoints = 0;

    @Column(name = "is_blocked", nullable = false)
    @Builder.Default
    private Boolean isBlocked = false;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "total_orders", nullable = false)
    @Builder.Default
    private Integer totalOrders = 0;

    @Column(name = "first_order_at")
    private OffsetDateTime firstOrderAt;

    @Column(name = "last_order_at")
    private OffsetDateTime lastOrderAt;
}