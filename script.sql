-- ==============================================================================
-- NEXOFOOD API - PostgreSQL Database Schema Script (script.sql)
-- Based on JPA Entities and Domain Architecture (Java 21 / Spring Boot 4)
-- All security, referential integrity, multi-tenant isolation, and 3NF
-- normalization improvements are applied directly inline within each module.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. EXTENSIONS
-- ------------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "postgis";

-- ------------------------------------------------------------------------------
-- 2. DROP EXISTING TABLES & FUNCTIONS (Reverse Dependency Order)
-- ------------------------------------------------------------------------------
DROP TABLE IF EXISTS inventory_movements CASCADE;
DROP TABLE IF EXISTS inventory_stocks CASCADE;
DROP TABLE IF EXISTS inventory_items CASCADE;
DROP TABLE IF EXISTS payments CASCADE;
DROP TABLE IF EXISTS order_items CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS cart_items CASCADE;
DROP TABLE IF EXISTS carts CASCADE;
DROP TABLE IF EXISTS product_prices CASCADE;
DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS categories CASCADE;
DROP TABLE IF EXISTS tenant_customers CASCADE;
DROP TABLE IF EXISTS tenant_members CASCADE;
DROP TABLE IF EXISTS tenants CASCADE;
DROP TABLE IF EXISTS subscriptions CASCADE;
DROP TABLE IF EXISTS subscription_plans CASCADE;
DROP TABLE IF EXISTS refresh_tokens CASCADE;
DROP TABLE IF EXISTS customer_addresses CASCADE;
DROP TABLE IF EXISTS users CASCADE;

DROP FUNCTION IF EXISTS update_updated_at_column CASCADE;
DROP FUNCTION IF EXISTS update_cart_total CASCADE;
DROP FUNCTION IF EXISTS sync_tenant_customer_stats CASCADE;

-- ------------------------------------------------------------------------------
-- 3. SHARED AUDIT TRIGGER FUNCTION: updated_at
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ==============================================================================
-- 4. MODULE: IDENTITY
-- ==============================================================================

-- Table: users
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    phone         VARCHAR(20),
    system_role   VARCHAR(50)  NOT NULL DEFAULT 'USER'
        CONSTRAINT chk_users_system_role CHECK (system_role IN ('SUPERADMIN', 'USER')),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Soft delete: preserva la identidad e integridad contable sin borrado físico
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table: customer_addresses
CREATE TABLE customer_addresses (
    id           UUID                   PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID                   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title        VARCHAR(50)            DEFAULT 'Casa',
    address_line TEXT                   NOT NULL,
    reference    TEXT,
    location     geometry(Point, 4326)  NOT NULL,
    created_at   TIMESTAMPTZ            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ            NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customer_addresses_user_id  ON customer_addresses(user_id);
CREATE INDEX idx_customer_addresses_location ON customer_addresses USING GIST(location);

CREATE TRIGGER trg_customer_addresses_updated_at
BEFORE UPDATE ON customer_addresses
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table: refresh_tokens
-- Seguridad: se almacena el hash criptográfico SHA-256 (nunca el token en texto plano).
CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expiry_date TIMESTAMP   NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_user_id   ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);

CREATE TRIGGER trg_refresh_tokens_updated_at
BEFORE UPDATE ON refresh_tokens
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- 5. MODULE: SUBSCRIPTION
-- ==============================================================================

-- Table: subscription_plans
CREATE TABLE subscription_plans (
    id                 UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(100)   NOT NULL,
    price              NUMERIC(10, 2) NOT NULL,
    billing_cycle_days INTEGER        NOT NULL DEFAULT 30,
    max_products       INTEGER,
    is_active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER trg_subscription_plans_updated_at
BEFORE UPDATE ON subscription_plans
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table: subscriptions
-- ON DELETE RESTRICT en user_id: protege el historial contable y legal de suscripciones.
CREATE TABLE subscriptions (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id           UUID        NOT NULL REFERENCES subscription_plans(id) ON DELETE RESTRICT,
    user_id           UUID        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status            VARCHAR(50) NOT NULL DEFAULT 'TRIAL'
        CONSTRAINT chk_subscriptions_status CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED')),
    start_date        TIMESTAMPTZ NOT NULL,
    end_date          TIMESTAMPTZ NOT NULL,
    mp_preapproval_id VARCHAR(100),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX idx_subscriptions_plan_id ON subscriptions(plan_id);

CREATE TRIGGER trg_subscriptions_updated_at
BEFORE UPDATE ON subscriptions
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- 6. MODULE: STORE
-- ==============================================================================

-- Table: tenants
-- Seguridad: credenciales de Mercado Pago almacenadas cifradas en reposo (AES-256-GCM / BYTEA).
CREATE TABLE tenants (
    id                   UUID                  PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id      UUID                  NOT NULL UNIQUE REFERENCES subscriptions(id) ON DELETE RESTRICT,
    owner_id             UUID                  NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    name                 VARCHAR(150)          NOT NULL,
    slug                 VARCHAR(100)          NOT NULL UNIQUE,
    logo_url             TEXT,
    banner_url           TEXT,
    phone                VARCHAR(20),
    address              TEXT,
    location             geometry(Point, 4326),
    delivery_radius_km   NUMERIC(5, 2)         DEFAULT 5.00,
    default_delivery_fee NUMERIC(10, 2)        DEFAULT 0.00,
    mp_access_token_enc  BYTEA,
    mp_public_key_enc    BYTEA,
    mp_refresh_token_enc BYTEA,
    mp_user_id           VARCHAR(100),
    mp_connected_at      TIMESTAMPTZ,
    is_active            BOOLEAN               NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ           NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenants_slug            ON tenants(slug);
CREATE INDEX idx_tenants_owner_id        ON tenants(owner_id);
CREATE INDEX idx_tenants_subscription_id ON tenants(subscription_id);
CREATE INDEX idx_tenants_location        ON tenants USING GIST(location);

CREATE TRIGGER trg_tenants_updated_at
BEFORE UPDATE ON tenants
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table: tenant_members
-- ON DELETE RESTRICT en user_id: preserva trazabilidad de membresías de staff.
CREATE TABLE tenant_members (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    role       VARCHAR(50) NOT NULL
        CONSTRAINT chk_tenant_members_role CHECK (role IN ('OWNER', 'ADMIN', 'COCINERO', 'REPARTIDOR')),
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_members_tenant_user UNIQUE (tenant_id, user_id)
);

CREATE INDEX idx_tenant_members_tenant_id ON tenant_members(tenant_id);
CREATE INDEX idx_tenant_members_user_id   ON tenant_members(user_id);

CREATE TRIGGER trg_tenant_members_updated_at
BEFORE UPDATE ON tenant_members
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE tenant_members ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tenant_members
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));

-- Table: tenant_customers
-- ON DELETE RESTRICT en user_id: garantiza que no se elimine en cascada la relación con órdenes.
-- total_orders, first_order_at, last_order_at: desnormalización por rendimiento (Read-Model),
-- mantenida de forma 100% consistente mediante el trigger trg_orders_sync_customer_stats.
CREATE TABLE tenant_customers (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id        UUID        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    loyalty_points INTEGER     NOT NULL DEFAULT 0,
    is_blocked     BOOLEAN     NOT NULL DEFAULT FALSE,
    notes          TEXT,
    total_orders   INTEGER     NOT NULL DEFAULT 0,
    first_order_at TIMESTAMPTZ,
    last_order_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_customer UNIQUE (tenant_id, user_id)
);

CREATE INDEX idx_tenant_customers_tenant_id ON tenant_customers(tenant_id);
CREATE INDEX idx_tenant_customers_user_id   ON tenant_customers(user_id);

CREATE TRIGGER trg_tenant_customers_updated_at
BEFORE UPDATE ON tenant_customers
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE tenant_customers ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tenant_customers
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));

-- ==============================================================================
-- 7. MODULE: CATALOG
-- ==============================================================================

-- Table: categories
CREATE TABLE categories (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_categories_tenant_id ON categories(tenant_id);

CREATE TRIGGER trg_categories_updated_at
BEFORE UPDATE ON categories
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON categories
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));

-- Table: products
-- UNIQUE(id, tenant_id): habilita llaves foráneas compuestas desde cart_items y order_items
-- para prevenir inyecciones cross-tenant a nivel de base de datos.
CREATE TABLE products (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    category_id  UUID         REFERENCES categories(id) ON DELETE SET NULL,
    name         VARCHAR(150) NOT NULL,
    description  TEXT,
    image_url    TEXT,
    is_available BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_products_id_tenant UNIQUE (id, tenant_id)
);

CREATE INDEX idx_products_tenant_id       ON products(tenant_id);
CREATE INDEX idx_products_category_id     ON products(category_id);
CREATE INDEX idx_products_tenant_available ON products(tenant_id, is_available);

CREATE TRIGGER trg_products_updated_at
BEFORE UPDATE ON products
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE products ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON products
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));

-- Table: product_prices
-- days_of_week: 1NF estricta mediante SMALLINT[] con valores ISO (1=Lunes ... 7=Domingo).
-- Permite búsquedas indexadas de alta velocidad mediante operador <@ e índice GIN.
CREATE TABLE product_prices (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id          UUID           NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    name                VARCHAR(100)   NOT NULL,
    price               NUMERIC(10, 2) NOT NULL,
    is_base             BOOLEAN        NOT NULL DEFAULT FALSE,
    discount_percentage NUMERIC(5, 2),
    start_date          DATE,
    end_date            DATE,
    start_time          TIME,
    end_time            TIME,
    days_of_week        SMALLINT[],
    priority            INTEGER        NOT NULL DEFAULT 0,
    is_active           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_product_prices_days_valid
        CHECK (days_of_week <@ ARRAY[1,2,3,4,5,6,7]::SMALLINT[])
);

CREATE INDEX idx_product_prices_product_id     ON product_prices(product_id);
CREATE INDEX idx_product_prices_product_active ON product_prices(product_id, is_active);
CREATE INDEX idx_product_prices_product_base   ON product_prices(product_id, is_base);
CREATE INDEX idx_product_prices_priority       ON product_prices(priority DESC);
CREATE INDEX idx_product_prices_days           ON product_prices USING GIN(days_of_week);

CREATE TRIGGER trg_product_prices_updated_at
BEFORE UPDATE ON product_prices
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- 8. MODULE: CART
-- ==============================================================================

-- Table: carts
-- total: columna sincronizada en tiempo real mediante el trigger trg_cart_items_sync_total.
CREATE TABLE carts (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID           NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    customer_id UUID           NOT NULL REFERENCES tenant_customers(id) ON DELETE CASCADE,
    total       NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    notes       TEXT,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_carts_tenant_customer UNIQUE (tenant_id, customer_id)
);

CREATE INDEX idx_carts_tenant_id   ON carts(tenant_id);
CREATE INDEX idx_carts_customer_id ON carts(customer_id);

CREATE TRIGGER trg_carts_updated_at
BEFORE UPDATE ON carts
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE carts ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON carts
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));

-- Table: cart_items
-- tenant_id + FK compuesta: previene cross-tenant leakage.
-- subtotal: 3NF estricta mediante GENERATED ALWAYS AS (quantity * unit_price) STORED.
CREATE TABLE cart_items (
    id         UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id    UUID           NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    tenant_id  UUID           NOT NULL,
    product_id UUID           NOT NULL,
    quantity   INTEGER        NOT NULL DEFAULT 1,
    unit_price NUMERIC(10, 2) NOT NULL,
    subtotal   NUMERIC(10, 2) GENERATED ALWAYS AS (quantity * unit_price) STORED,
    notes      TEXT,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cart_items_product_tenant_fkey
        FOREIGN KEY (product_id, tenant_id) REFERENCES products(id, tenant_id)
);

CREATE INDEX idx_cart_items_cart_id    ON cart_items(cart_id);
CREATE INDEX idx_cart_items_product_id ON cart_items(product_id);
CREATE INDEX idx_cart_items_tenant_id  ON cart_items(tenant_id);

CREATE TRIGGER trg_cart_items_updated_at
BEFORE UPDATE ON cart_items
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE cart_items ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON cart_items
    USING (cart_id IN (SELECT id FROM carts));

-- Trigger de sincronización de total en carts
CREATE OR REPLACE FUNCTION update_cart_total()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE carts SET total = (
        SELECT COALESCE(SUM(quantity * unit_price), 0.00)
        FROM cart_items
        WHERE cart_id = COALESCE(NEW.cart_id, OLD.cart_id)
    ) WHERE id = COALESCE(NEW.cart_id, OLD.cart_id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_cart_items_sync_total
AFTER INSERT OR UPDATE OR DELETE ON cart_items
FOR EACH ROW EXECUTE FUNCTION update_cart_total();

-- ==============================================================================
-- 9. MODULE: ORDER
-- ==============================================================================

-- Table: orders
-- total: GENERATED ALWAYS AS (subtotal + delivery_fee) STORED.
-- CONSTRAINT uk_orders_tenant_number: unicidad de número de pedido por tenant.
-- address_id: FK opcional hacia customer_addresses para trazabilidad de recurrencia.
CREATE TABLE orders (
    id                UUID                  PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID                  NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    customer_id       UUID                  NOT NULL REFERENCES tenant_customers(id) ON DELETE RESTRICT,
    delivery_staff_id UUID                  REFERENCES users(id) ON DELETE SET NULL,
    address_id        UUID                  REFERENCES customer_addresses(id) ON DELETE SET NULL,
    order_number      VARCHAR(20)           NOT NULL,
    delivery_type     VARCHAR(50)           NOT NULL DEFAULT 'DELIVERY'
        CONSTRAINT chk_orders_delivery_type CHECK (delivery_type IN ('DELIVERY', 'TAKEAWAY', 'DINE_IN')),
    status            VARCHAR(50)           NOT NULL DEFAULT 'PENDIENTE'
        CONSTRAINT chk_orders_status CHECK (status IN ('PENDIENTE', 'EN_PREPARACION', 'LISTO_PARA_ENTREGA', 'EN_CAMINO', 'ENTREGADO', 'CANCELADO')),
    delivery_address  TEXT,
    delivery_location geometry(Point, 4326),
    subtotal          NUMERIC(10, 2)        NOT NULL,
    delivery_fee      NUMERIC(10, 2)        NOT NULL DEFAULT 0.00,
    total             NUMERIC(10, 2)        GENERATED ALWAYS AS (subtotal + delivery_fee) STORED,
    notes             TEXT,
    created_at        TIMESTAMPTZ           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_orders_tenant_number UNIQUE (tenant_id, order_number)
);

CREATE INDEX idx_orders_tenant_id          ON orders(tenant_id);
CREATE INDEX idx_orders_customer_id        ON orders(customer_id);
CREATE INDEX idx_orders_delivery_staff_id  ON orders(delivery_staff_id);
CREATE INDEX idx_orders_address_id         ON orders(address_id);
CREATE INDEX idx_orders_tenant_status      ON orders(tenant_id, status);
CREATE INDEX idx_orders_delivery_location  ON orders USING GIST(delivery_location);

CREATE TRIGGER trg_orders_updated_at
BEFORE UPDATE ON orders
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON orders
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));

-- Trigger de sincronización de estadísticas acumuladas en tenant_customers
CREATE OR REPLACE FUNCTION sync_tenant_customer_stats()
RETURNS TRIGGER AS $$
DECLARE
    v_customer_id UUID;
BEGIN
    v_customer_id := COALESCE(NEW.customer_id, OLD.customer_id);

    UPDATE tenant_customers SET
        total_orders   = (SELECT COUNT(*) FROM orders WHERE customer_id = v_customer_id AND status != 'CANCELADO'),
        first_order_at = (SELECT MIN(created_at) FROM orders WHERE customer_id = v_customer_id AND status != 'CANCELADO'),
        last_order_at  = (SELECT MAX(created_at) FROM orders WHERE customer_id = v_customer_id AND status != 'CANCELADO')
    WHERE id = v_customer_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_orders_sync_customer_stats
AFTER INSERT OR UPDATE OF status OR DELETE ON orders
FOR EACH ROW EXECUTE FUNCTION sync_tenant_customer_stats();

-- Table: order_items
-- tenant_id + FK compuesta: aislamiento estricto por tenant.
-- product_name: fotografía histórica inmutable del ítem al emitir la comanda.
-- subtotal: GENERATED ALWAYS AS (quantity * unit_price) STORED.
CREATE TABLE order_items (
    id           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID           NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    tenant_id    UUID           NOT NULL,
    product_id   UUID,
    product_name VARCHAR(150)   NOT NULL,
    unit_price   NUMERIC(10, 2) NOT NULL,
    quantity     INTEGER        NOT NULL,
    subtotal     NUMERIC(10, 2) GENERATED ALWAYS AS (quantity * unit_price) STORED,
    notes        TEXT,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT order_items_product_tenant_fkey
        FOREIGN KEY (product_id, tenant_id) REFERENCES products(id, tenant_id)
);

CREATE INDEX idx_order_items_order_id   ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);
CREATE INDEX idx_order_items_tenant_id  ON order_items(tenant_id);

CREATE TRIGGER trg_order_items_updated_at
BEFORE UPDATE ON order_items
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE order_items ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON order_items
    USING (order_id IN (SELECT id FROM orders));

-- ==============================================================================
-- 10. MODULE: PAYMENT
-- ==============================================================================

-- Table: payments
-- raw_response: JSONB sanitizado por la aplicación, excluyendo datos de tarjeta y PII.
CREATE TABLE payments (
    id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id         UUID           NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    tenant_id        UUID           NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    mp_payment_id    VARCHAR(100),
    mp_preference_id VARCHAR(100),
    payment_method   VARCHAR(50),
    status           VARCHAR(50)    NOT NULL DEFAULT 'PENDING'
        CONSTRAINT chk_payments_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REFUNDED')),
    amount           NUMERIC(10, 2) NOT NULL,
    raw_response     JSONB,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payments_order_id       ON payments(order_id);
CREATE INDEX idx_payments_tenant_id      ON payments(tenant_id);
CREATE INDEX idx_payments_mp_payment_id  ON payments(mp_payment_id);
CREATE INDEX idx_payments_tenant_status  ON payments(tenant_id, status);

CREATE TRIGGER trg_payments_updated_at
BEFORE UPDATE ON payments
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON payments
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));

-- ==============================================================================
-- 11. MODULE: INVENTORY
-- ==============================================================================

-- Table: inventory_items
CREATE TABLE inventory_items (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID           NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        VARCHAR(150)   NOT NULL,
    sku         VARCHAR(50),
    description TEXT,
    category    VARCHAR(100),
    unit        VARCHAR(20)    NOT NULL DEFAULT 'UNIT'
        CONSTRAINT chk_inventory_items_unit CHECK (unit IN ('UNIT', 'KG')),
    cost_price  NUMERIC(10, 2) DEFAULT 0.00,
    is_active   BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inventory_items_tenant_id  ON inventory_items(tenant_id);
CREATE INDEX idx_inventory_items_tenant_sku ON inventory_items(tenant_id, sku);

CREATE TRIGGER trg_inventory_items_updated_at
BEFORE UPDATE ON inventory_items
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE inventory_items ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON inventory_items
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));

-- Table: inventory_stocks
-- CHECKs de no-negatividad: garantizan que el stock nunca caiga bajo cero.
CREATE TABLE inventory_stocks (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID           NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    inventory_item_id UUID           NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
    quantity          NUMERIC(12, 3) NOT NULL DEFAULT 0.000
        CONSTRAINT chk_inventory_stocks_quantity_non_negative CHECK (quantity >= 0),
    reserved_quantity NUMERIC(12, 3) NOT NULL DEFAULT 0.000
        CONSTRAINT chk_inventory_stocks_reserved_non_negative CHECK (reserved_quantity >= 0),
    minimum_stock     NUMERIC(12, 3) NOT NULL DEFAULT 0.000,
    maximum_stock     NUMERIC(12, 3),
    location          VARCHAR(150),
    is_active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inventory_stocks_tenant_item UNIQUE (tenant_id, inventory_item_id)
);

CREATE INDEX idx_inventory_stocks_tenant_id ON inventory_stocks(tenant_id);
CREATE INDEX idx_inventory_stocks_item_id   ON inventory_stocks(inventory_item_id);

CREATE TRIGGER trg_inventory_stocks_updated_at
BEFORE UPDATE ON inventory_stocks
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE inventory_stocks ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON inventory_stocks
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));

-- Table: inventory_movements
-- performed_by_name: fotografía histórica del operador (audit snapshot).
-- CHECK de consistencia: new_quantity = previous_quantity ± quantity.
CREATE TABLE inventory_movements (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    inventory_stock_id  UUID           NOT NULL REFERENCES inventory_stocks(id) ON DELETE CASCADE,
    inventory_item_id   UUID           NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
    movement_type       VARCHAR(50)    NOT NULL
        CONSTRAINT chk_inventory_movements_type CHECK (movement_type IN ('ENTRY', 'EXIT', 'ADJUSTMENT')),
    reason              VARCHAR(50)    NOT NULL
        CONSTRAINT chk_inventory_movements_reason CHECK (reason IN (
            'PURCHASE', 'INITIAL_STOCK', 'CUSTOMER_RETURN', 'TRANSFER_IN',
            'KITCHEN_CONSUMPTION_OR_WASTE', 'EXPIRED_OR_SPOILAGE', 'DAMAGED_OR_LOSS',
            'SALE', 'INTERNAL_CONSUMPTION', 'SUPPLIER_RETURN', 'TRANSFER_OUT',
            'PHYSICAL_COUNT', 'CORRECTION', 'OTHER'
        )),
    quantity            NUMERIC(12, 3) NOT NULL,
    previous_quantity   NUMERIC(12, 3) NOT NULL,
    new_quantity        NUMERIC(12, 3) NOT NULL,
    performed_by_id     UUID           NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    performed_by_name   VARCHAR(150),
    reason_details      TEXT           NOT NULL,
    reference_id        UUID,
    reference_type      VARCHAR(50),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_inventory_movements_quantity_consistency
        CHECK (
            (movement_type = 'ENTRY'      AND new_quantity = previous_quantity + quantity) OR
            (movement_type = 'EXIT'       AND new_quantity = previous_quantity - quantity) OR
            (movement_type = 'ADJUSTMENT')
        )
);

CREATE INDEX idx_inventory_movements_tenant_id    ON inventory_movements(tenant_id);
CREATE INDEX idx_inventory_movements_stock_id     ON inventory_movements(inventory_stock_id);
CREATE INDEX idx_inventory_movements_item_id      ON inventory_movements(inventory_item_id);
CREATE INDEX idx_inventory_movements_performed_by ON inventory_movements(performed_by_id);
CREATE INDEX idx_inventory_movements_created_at   ON inventory_movements(tenant_id, created_at DESC);
CREATE INDEX idx_inventory_movements_reference    ON inventory_movements(reference_id, reference_type);

CREATE TRIGGER trg_inventory_movements_updated_at
BEFORE UPDATE ON inventory_movements
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE inventory_movements ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON inventory_movements
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));
