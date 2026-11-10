package lat.nexofood.api.modules.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lat.nexofood.api.common.model.BaseEntity;
import lat.nexofood.api.modules.order.application.dto.OrderDraft;
import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.store.domain.Tenant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @EqualsAndHashCode.Include
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true)
    @ToString.Exclude
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    @ToString.Exclude
    private Tenant tenant;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "mp_order_id", length = 100, unique = true)
    private String mpOrderId;

    @Column(name = "mp_payment_id", length = 100)
    private String mpPaymentId;

    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "status_detail", length = 100)
    private String statusDetail;

    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    @Builder.Default
    private String currency = "PEN";

    @ToString.Exclude
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checkout_snapshot", columnDefinition = "jsonb")
    private OrderDraft checkoutSnapshot;

    @ToString.Exclude
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private String rawResponse;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "refunded_at")
    private OffsetDateTime refundedAt;

    @Column(name = "refund_reason", columnDefinition = "TEXT")
    private String refundReason;

    @PrePersist
    @PreUpdate
    private void syncCustomerId() {
        if (this.customerId == null) {
            if (this.checkoutSnapshot != null && this.checkoutSnapshot.customerUserId() != null) {
                this.customerId = this.checkoutSnapshot.customerUserId();
            } else if (this.order != null && this.order.getCustomerId() != null) {
                this.customerId = this.order.getCustomerId();
            }
        }
    }

    public boolean canBeRefunded() {
        return status == PaymentStatus.APPROVED;
    }

    public boolean isAlreadyRefundedOrPendingRefund() {
        return status == PaymentStatus.REFUNDED || status == PaymentStatus.REFUND_PENDING;
    }
}
