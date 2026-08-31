package lat.nexofood.api.modules.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lat.nexofood.api.common.model.BaseEntity;
import lat.nexofood.api.modules.auth.domain.User;
import lat.nexofood.api.modules.tenant.domain.Tenant;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @EqualsAndHashCode.Include
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    @ToString.Exclude
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    @ToString.Exclude
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_staff_id")
    @ToString.Exclude
    private User deliveryStaff;

    @Column(name = "order_number", length = 20, nullable = false)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false, length = 50)
    @Builder.Default
    private DeliveryType deliveryType = DeliveryType.DELIVERY;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDIENTE;

    @Column(name = "delivery_address", columnDefinition = "TEXT")
    private String deliveryAddress;

    @Column(name = "delivery_location", columnDefinition = "geometry(Point, 4326)")
    private Point deliveryLocation;

    @Column(name = "subtotal", precision = 10, scale = 2, nullable = false)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal deliveryFee = new BigDecimal("0.00");

    @Column(name = "total", precision = 10, scale = 2, nullable = false)
    private BigDecimal total;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();
}
