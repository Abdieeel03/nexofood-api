-- ==============================================================================
-- MIGRACIÓN: Eliminación del módulo Inventory y stock directo en Product
-- Fecha: 2026-11
-- ==============================================================================

-- 1. Eliminar tablas obsoletas del módulo Inventory (orden inverso de dependencias)
DROP TABLE IF EXISTS product_ingredients CASCADE;
DROP TABLE IF EXISTS inventory_movements CASCADE;
DROP TABLE IF EXISTS inventory_stocks CASCADE;
DROP TABLE IF EXISTS inventory_items CASCADE;

-- 2. Agregar columna stock entero no-negativo en products
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS stock INTEGER NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_products_stock_non_negative'
          AND conrelid = 'public.products'::regclass
    ) THEN
        ALTER TABLE products
            ADD CONSTRAINT chk_products_stock_non_negative CHECK (stock >= 0);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_products_tenant_stock ON products(tenant_id, stock);
