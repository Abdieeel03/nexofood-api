package lat.nexofood.api.modules.analytics.infrastructure.repository;

import lat.nexofood.api.modules.order.domain.Order;
import lat.nexofood.api.modules.order.domain.OrderStatus;
import lat.nexofood.api.modules.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface FinancialAnalyticsRepository extends JpaRepository<Order, UUID> {

    @Query("""
            SELECT
                COUNT(o.id) AS orderCount,
                COALESCE(SUM(o.subtotal), 0) AS netSales,
                COALESCE(SUM(o.taxTotal), 0) AS taxCollected,
                COALESCE(SUM(o.deliveryFee), 0) AS deliveryFees
            FROM Order o
            WHERE o.tenant.id = :tenantId
              AND o.status <> :canceledStatus
              AND o.createdAt >= :fromDateTime
              AND o.createdAt < :toDateTimeExclusive
              AND (
                :paidOnly = false
                OR EXISTS (
                    SELECT 1 FROM Payment p
                    WHERE p.order.id = o.id
                      AND p.status = :approvedStatus
                )
              )
            """)
    OrderFinancialTotalsProjection aggregateOrderFinancialTotals(
            @Param("tenantId") UUID tenantId,
            @Param("canceledStatus") OrderStatus canceledStatus,
            @Param("approvedStatus") PaymentStatus approvedStatus,
            @Param("fromDateTime") OffsetDateTime fromDateTime,
            @Param("toDateTimeExclusive") OffsetDateTime toDateTimeExclusive,
            @Param("paidOnly") boolean paidOnly
    );

    @Query("""
            SELECT
                COALESCE(SUM(oi.quantity), 0) AS unitsSold,
                COALESCE(SUM(oi.unitCost * oi.quantity), 0) AS costOfGoodsSold
            FROM OrderItem oi
            JOIN oi.order o
            WHERE o.tenant.id = :tenantId
              AND o.status <> :canceledStatus
              AND o.createdAt >= :fromDateTime
              AND o.createdAt < :toDateTimeExclusive
              AND (
                :paidOnly = false
                OR EXISTS (
                    SELECT 1 FROM Payment p
                    WHERE p.order.id = o.id
                      AND p.status = :approvedStatus
                )
              )
            """)
    OrderItemsCostTotalsProjection aggregateOrderItemsCostTotals(
            @Param("tenantId") UUID tenantId,
            @Param("canceledStatus") OrderStatus canceledStatus,
            @Param("approvedStatus") PaymentStatus approvedStatus,
            @Param("fromDateTime") OffsetDateTime fromDateTime,
            @Param("toDateTimeExclusive") OffsetDateTime toDateTimeExclusive,
            @Param("paidOnly") boolean paidOnly
    );

    @Query("""
            SELECT
                p.id AS productId,
                MAX(oi.productName) AS productName,
                COALESCE(SUM(oi.quantity), 0) AS unitsSold,
                COALESCE(SUM(oi.unitPrice * oi.quantity), 0) AS netSales,
                COALESCE(SUM(oi.taxAmount), 0) AS taxCollected,
                COALESCE(SUM(oi.unitCost * oi.quantity), 0) AS totalCost
            FROM OrderItem oi
            JOIN oi.order o
            LEFT JOIN oi.product p
            WHERE o.tenant.id = :tenantId
              AND o.status <> :canceledStatus
              AND o.createdAt >= :fromDateTime
              AND o.createdAt < :toDateTimeExclusive
              AND (
                :paidOnly = false
                OR EXISTS (
                    SELECT 1 FROM Payment pay
                    WHERE pay.order.id = o.id
                      AND pay.status = :approvedStatus
                )
              )
            GROUP BY p.id, oi.productName
            ORDER BY COALESCE(SUM(oi.unitPrice * oi.quantity), 0) - COALESCE(SUM(oi.unitCost * oi.quantity), 0) DESC
            """)
    List<ProductMarginProjection> aggregateProductMargins(
            @Param("tenantId") UUID tenantId,
            @Param("canceledStatus") OrderStatus canceledStatus,
            @Param("approvedStatus") PaymentStatus approvedStatus,
            @Param("fromDateTime") OffsetDateTime fromDateTime,
            @Param("toDateTimeExclusive") OffsetDateTime toDateTimeExclusive,
            @Param("paidOnly") boolean paidOnly
    );

    interface OrderFinancialTotalsProjection {
        Long getOrderCount();
        BigDecimal getNetSales();
        BigDecimal getTaxCollected();
        BigDecimal getDeliveryFees();
    }

    interface OrderItemsCostTotalsProjection {
        Long getUnitsSold();
        BigDecimal getCostOfGoodsSold();
    }

    interface ProductMarginProjection {
        UUID getProductId();
        String getProductName();
        Long getUnitsSold();
        BigDecimal getNetSales();
        BigDecimal getTaxCollected();
        BigDecimal getTotalCost();
    }
}
