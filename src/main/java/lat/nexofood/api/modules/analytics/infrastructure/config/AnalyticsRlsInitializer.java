package lat.nexofood.api.modules.analytics.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Inicializador automático que aplica en el arranque los índices compuestos de analítica
 * y las políticas de Row-Level Security (RLS) multi-tenant sobre {@code tenant_expenses}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsRlsInitializer {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeAnalyticsIndexesAndRls() {
        try {
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_orders_tenant_status_created_at " +
                    "ON orders (tenant_id, status, created_at)"
            );
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_tenant_expenses_tenant_date " +
                    "ON tenant_expenses (tenant_id, expense_date DESC)"
            );
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_tenant_expenses_tenant_type_date " +
                    "ON tenant_expenses (tenant_id, type, expense_date)"
            );

            jdbcTemplate.execute("ALTER TABLE tenant_expenses ENABLE ROW LEVEL SECURITY");
            jdbcTemplate.execute("ALTER TABLE tenant_expenses FORCE ROW LEVEL SECURITY");
            jdbcTemplate.execute(
                    "DO $$ BEGIN " +
                    "IF NOT EXISTS (" +
                    "  SELECT 1 FROM pg_policies WHERE tablename = 'tenant_expenses' AND policyname = 'tenant_isolation'" +
                    ") THEN " +
                    "  CREATE POLICY tenant_isolation ON tenant_expenses " +
                    "    USING (tenant_id::TEXT = (SELECT current_setting('app.current_tenant_id', true))); " +
                    "END IF; " +
                    "END $$;"
            );

            log.info("Índices analíticos y política RLS activados en tenant_expenses.");
        } catch (Exception ex) {
            log.debug("Omitiendo inicialización DDL/RLS para analytics (entorno de test o sin privilegios DDL): {}",
                    ex.getMessage());
        }
    }
}
