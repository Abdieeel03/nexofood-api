package lat.nexofood.api.modules.analytics.infrastructure.repository;

import lat.nexofood.api.modules.analytics.domain.ExpenseCategory;
import lat.nexofood.api.modules.analytics.domain.ExpenseType;
import lat.nexofood.api.modules.analytics.domain.TenantExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantExpenseRepository extends JpaRepository<TenantExpense, UUID>, JpaSpecificationExecutor<TenantExpense> {

    Optional<TenantExpense> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
            SELECT e.type AS type, COALESCE(SUM(e.amount), 0) AS totalAmount
            FROM TenantExpense e
            WHERE e.tenant.id = :tenantId
              AND e.expenseDate >= :fromDate
              AND e.expenseDate <= :toDate
            GROUP BY e.type
            """)
    List<ExpenseTypeSummaryProjection> sumByTypeForTenantAndPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            SELECT e.category AS category, e.type AS type, COALESCE(SUM(e.amount), 0) AS totalAmount
            FROM TenantExpense e
            WHERE e.tenant.id = :tenantId
              AND e.expenseDate >= :fromDate
              AND e.expenseDate <= :toDate
            GROUP BY e.category, e.type
            ORDER BY COALESCE(SUM(e.amount), 0) DESC
            """)
    List<ExpenseCategorySummaryProjection> sumByCategoryForTenantAndPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    interface ExpenseTypeSummaryProjection {
        ExpenseType getType();
        BigDecimal getTotalAmount();
    }

    interface ExpenseCategorySummaryProjection {
        ExpenseCategory getCategory();
        ExpenseType getType();
        BigDecimal getTotalAmount();
    }
}
