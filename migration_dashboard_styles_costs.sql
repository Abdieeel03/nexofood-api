-- ==============================================================================
-- NEXOFOOD - MIGRACIÓN INCREMENTAL IDEMPOTENTE (PostgreSQL 16+ / Supabase)
-- ==============================================================================
-- Propósito:
--   Aplicar sobre una base de datos existente (especialmente en entornos con
--   spring.jpa.hibernate.ddl-auto=validate) los cambios de:
--   1. tenants.storefront_style (estilos predefinidos de storefront)
--   2. products.cost (costo unitario del producto)
--   3. order_items.unit_cost (snapshot histórico de costo al momento de la venta)
--   4. tenant_expenses (gastos operativos, no operativos e impuesto a la renta) + RLS
--   5. Índice compuesto en orders(tenant_id, status, created_at) para el dashboard
-- ==============================================================================

-- 1. tenants.storefront_style
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS storefront_style VARCHAR(50) NOT NULL DEFAULT 'MODERN';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_tenants_storefront_style'
          AND conrelid = 'public.tenants'::regclass
    ) THEN
        ALTER TABLE tenants
            ADD CONSTRAINT chk_tenants_storefront_style
            CHECK (storefront_style IN ('MODERN', 'MINIMALIST', 'ENTERPRISE', 'CLASSIC'));
    END IF;
END $$;

-- 2. products.cost
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS cost NUMERIC(10, 2) NOT NULL DEFAULT 0.00;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_products_cost_non_negative'
          AND conrelid = 'public.products'::regclass
    ) THEN
        ALTER TABLE products
            ADD CONSTRAINT chk_products_cost_non_negative CHECK (cost >= 0);
    END IF;
END $$;

-- 3. order_items.unit_cost
ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS unit_cost NUMERIC(10, 2) NOT NULL DEFAULT 0.00;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_order_items_unit_cost_non_negative'
          AND conrelid = 'public.order_items'::regclass
    ) THEN
        ALTER TABLE order_items
            ADD CONSTRAINT chk_order_items_unit_cost_non_negative CHECK (unit_cost >= 0);
    END IF;
END $$;

-- 4. Índice compuesto en orders para consultas del dashboard financiero
CREATE INDEX IF NOT EXISTS idx_orders_tenant_status_created_at
    ON orders(tenant_id, status, created_at);

-- 5. Tabla tenant_expenses + índices + trigger + RLS
CREATE TABLE IF NOT EXISTS tenant_expenses (
    id           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID           NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    created_by   UUID           REFERENCES users(id) ON DELETE SET NULL,
    type         VARCHAR(30)    NOT NULL DEFAULT 'OPERATING'
        CONSTRAINT chk_tenant_expenses_type CHECK (type IN ('OPERATING', 'NON_OPERATING', 'INCOME_TAX')),
    category     VARCHAR(40)    NOT NULL DEFAULT 'OTHER'
        CONSTRAINT chk_tenant_expenses_category CHECK (category IN (
            'RENT', 'PAYROLL', 'UTILITIES', 'MARKETING', 'PACKAGING',
            'PLATFORM_FEES', 'MAINTENANCE', 'INCOME_TAX', 'FINANCIAL', 'OTHER'
        )),
    description  VARCHAR(255)   NOT NULL,
    amount       NUMERIC(10, 2) NOT NULL
        CONSTRAINT chk_tenant_expenses_amount_positive CHECK (amount > 0),
    expense_date DATE           NOT NULL,
    notes        TEXT,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tenant_expenses_tenant_date
    ON tenant_expenses(tenant_id, expense_date DESC);

CREATE INDEX IF NOT EXISTS idx_tenant_expenses_tenant_type_date
    ON tenant_expenses(tenant_id, type, expense_date);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'trg_tenant_expenses_updated_at'
          AND tgrelid = 'public.tenant_expenses'::regclass
    ) THEN
        CREATE TRIGGER trg_tenant_expenses_updated_at
        BEFORE UPDATE ON tenant_expenses
        FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

ALTER TABLE tenant_expenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_expenses FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public'
          AND tablename = 'tenant_expenses'
          AND policyname = 'tenant_isolation'
    ) THEN
        CREATE POLICY tenant_isolation ON tenant_expenses
            USING (tenant_id::TEXT = (SELECT current_setting('app.current_tenant_id', true)));
    END IF;
END $$;

-- 6. Permisos para el rol de aplicación (si existe nexofood_app)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nexofood_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE tenant_expenses TO nexofood_app;
    END IF;
END $$;
