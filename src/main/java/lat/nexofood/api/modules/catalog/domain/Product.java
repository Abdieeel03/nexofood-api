package lat.nexofood.api.modules.catalog.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lat.nexofood.api.common.exception.BadRequestException;
import lat.nexofood.api.common.model.BaseEntity;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Product extends BaseEntity {

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
    @JoinColumn(name = "category_id")
    @ToString.Exclude
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_id")
    @ToString.Exclude
    private Tax tax;

    @Column(name = "name", length = 150, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "cost", precision = 10, scale = 2, nullable = false, columnDefinition = "NUMERIC(10, 2) DEFAULT 0.00")
    @Builder.Default
    private BigDecimal cost = new BigDecimal("0.00");

    @Column(name = "stock", nullable = false)
    @Builder.Default
    private Integer stock = 0;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private Boolean isAvailable = true;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    private List<ProductPrice> prices = new ArrayList<>();

    public boolean hasSufficientStock(int requestedQuantity) {
        return requestedQuantity > 0 && this.stock != null && this.stock >= requestedQuantity;
    }

    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new BadRequestException("La cantidad a descontar del stock debe ser mayor a cero.");
        }
        int current = this.stock != null ? this.stock : 0;
        if (current < quantity) {
            throw new BadRequestException(
                    "Stock insuficiente para el producto '" + this.name + "'. Disponible: "
                            + current + ", solicitado: " + quantity + "."
            );
        }
        this.stock = current - quantity;
    }

    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new BadRequestException("La cantidad a restaurar en el stock debe ser mayor a cero.");
        }
        int current = this.stock != null ? this.stock : 0;
        this.stock = current + quantity;
    }

    public void updateStock(int newStock) {
        if (newStock < 0) {
            throw new BadRequestException("El stock del producto no puede ser negativo.");
        }
        this.stock = newStock;
    }
}
