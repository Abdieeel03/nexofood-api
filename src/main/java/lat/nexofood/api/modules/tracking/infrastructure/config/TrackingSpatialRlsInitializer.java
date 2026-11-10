package lat.nexofood.api.modules.tracking.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Inicializador automático que aplica en el arranque los índices espaciales GIST (PostGIS)
 * y las políticas de Row-Level Security (RLS) multi-tenant sobre las tablas de rastreo
 * creadas por Hibernate JPA ({@code delivery_tracking} y {@code delivery_location_history}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackingSpatialRlsInitializer {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeSpatialIndexesAndRls() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS postgis");

            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_delivery_tracking_location " +
                    "ON delivery_tracking USING GIST (location)"
            );
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_delivery_location_history_location " +
                    "ON delivery_location_history USING GIST (location)"
            );

            enableRlsForTable("delivery_tracking");
            enableRlsForTable("delivery_location_history");

            log.info("Índices espaciales GIST (PostGIS) y políticas RLS activadas en tablas de tracking.");
        } catch (Exception ex) {
            log.debug("Omitiendo inicialización DDL PostGIS/RLS para tracking (entorno de test o sin privilegios DDL): {}",
                    ex.getMessage());
        }
    }

    private void enableRlsForTable(String tableName) {
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ENABLE ROW LEVEL SECURITY");
        jdbcTemplate.execute("ALTER TABLE " + tableName + " FORCE ROW LEVEL SECURITY");
        jdbcTemplate.execute(
                "DO $$ BEGIN " +
                "IF NOT EXISTS (" +
                "  SELECT 1 FROM pg_policies WHERE tablename = '" + tableName + "' AND policyname = 'tenant_isolation'" +
                ") THEN " +
                "  CREATE POLICY tenant_isolation ON " + tableName +
                "    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true)); " +
                "END IF; " +
                "END $$;"
        );
    }
}

