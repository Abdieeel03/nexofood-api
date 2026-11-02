package lat.nexofood.api.modules.cart.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.model.BaseEntity;
import lat.nexofood.api.modules.catalog.domain.Product;
import lat.nexofood.api.modules.store.domain.Tenant;
import lat.nexofood.api.modules.store.domain.TenantCustomer;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(
        name = "carts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_carts_tenant_customer", columnNames = {"tenant_id", "customer_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Cart extends BaseEntity {

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
    @JoinColumns({
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", insertable = false, updatable = false, nullable = false),
            @JoinColumn(name = "customer_id", referencedColumnName = "user_id", insertable = false, updatable = false, nullable = false)
    })
    @ToString.Exclude
    private TenantCustomer customer;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "total", precision = 10, scale = 2, insertable = false, updatable = false)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    @PrePersist
    @PreUpdate
    private void syncCustomerId() {
        if (this.customerId == null && this.customer != null) {
            if (this.customer.getUser() != null && this.customer.getUser().getId() != null) {
                this.customerId = this.customer.getUser().getId();
            } else if (this.customer.getId() != null) {
                this.customerId = this.customer.getId().getUserId();
            }
        }
    }

    public void setCustomer(TenantCustomer customer) {
        this.customer = customer;
        if (customer != null) {
            if (customer.getUser() != null && customer.getUser().getId() != null) {
                this.customerId = customer.getUser().getId();
            } else if (customer.getId() != null) {
                this.customerId = customer.getId().getUserId();
            }
        }
    }

    public CartItem addItem(Product product, int quantity, BigDecimal unitPrice, String notes) {
        if (product == null) {
            throw new BadRequestException("El producto es obligatorio para agregar al carrito");
        }
        if (quantity <= 0) {
            throw new BadRequestException("La cantidad del ítem debe ser mayor a 0");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("El precio unitario no puede ser nulo ni negativo");
        }

        if (this.items == null) {
            this.items = new ArrayList<>();
        }

        Optional<CartItem> existingOpt = this.items.stream()
                .filter(i -> i.getProduct() != null
                        && product.getId() != null
                        && product.getId().equals(i.getProduct().getId()))
                .findFirst();

        if (existingOpt.isPresent()) {
            CartItem existing = existingOpt.get();
            existing.increaseQuantity(quantity);
            existing.repriceTo(unitPrice);
            if (notes != null && !notes.isBlank()) {
                existing.setNotes(notes);
            }
            calculateTotal();
            return existing;
        }

        CartItem newItem = CartItem.builder()
                .cart(this)
                .tenant(this.tenant != null ? this.tenant : product.getTenant())
                .product(product)
                .quantity(quantity)
                .unitPrice(unitPrice.setScale(2, RoundingMode.HALF_UP))
                .notes(notes)
                .build();
        newItem.calculateSubtotal();
        this.items.add(newItem);
        calculateTotal();
        return newItem;
    }

    public CartItem addItem(CartItem item) {
        if (item == null) {
            throw new BadRequestException("El ítem no puede ser nulo");
        }
        return addItem(
                item.getProduct(),
                item.getQuantity() != null ? item.getQuantity() : 1,
                item.getUnitPrice(),
                item.getNotes()
        );
    }

    public boolean removeItem(UUID itemId) {
        if (itemId == null || this.items == null) {
            return false;
        }
        boolean removed = this.items.removeIf(item -> itemId.equals(item.getId()));
        calculateTotal();
        return removed;
    }

    public void clear() {
        if (this.items != null) {
            this.items.clear();
        }
        this.total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateTotal() {
        if (this.items == null || this.items.isEmpty()) {
            this.total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            return this.total;
        }
        BigDecimal sum = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (CartItem item : this.items) {
            sum = sum.add(item.calculateSubtotal());
        }
        this.total = sum.setScale(2, RoundingMode.HALF_UP);
        return this.total;
    }
}
