package lat.nexofood.api.modules.cart.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.model.BaseEntity;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.store.domain.Tenant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class CartItem extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @EqualsAndHashCode.Include
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    @ToString.Exclude
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    @ToString.Exclude
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @ToString.Exclude
    private Product product;

    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "subtotal", precision = 10, scale = 2, insertable = false, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public void increaseQuantity(int delta) {
        if (delta <= 0) {
            throw new BadRequestException("El incremento de cantidad debe ser mayor a 0");
        }
        int current = this.quantity != null ? this.quantity : 0;
        this.quantity = current + delta;
        calculateSubtotal();
    }

    public void updateQuantity(int newQuantity) {
        if (newQuantity <= 0) {
            throw new BadRequestException("La cantidad del ítem debe ser mayor a 0");
        }
        this.quantity = newQuantity;
        calculateSubtotal();
    }

    public void repriceTo(BigDecimal newUnitPrice) {
        if (newUnitPrice == null || newUnitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("El precio unitario no puede ser nulo ni negativo");
        }
        this.unitPrice = newUnitPrice.setScale(2, RoundingMode.HALF_UP);
        calculateSubtotal();
    }

    public BigDecimal calculateSubtotal() {
        if (this.unitPrice == null || this.quantity == null || this.quantity <= 0) {
            this.subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            return this.subtotal;
        }
        this.subtotal = this.unitPrice
                .multiply(BigDecimal.valueOf(this.quantity))
                .setScale(2, RoundingMode.HALF_UP);
        return this.subtotal;
    }
}
