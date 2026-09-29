-- ==============================================================================
-- NEXOFOOD API - PostgreSQL Database Schema Script (script.sql)
-- Generated based on JPA Entities and Domain Architecture (Java 21 / Spring Boot 4)
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. EXTENSIONS
-- ------------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "postgis";

-- ------------------------------------------------------------------------------
-- 2. DROP EXISTING TABLES (Reverse Dependency Order)
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

-- Drop function if exists
DROP FUNCTION IF EXISTS update_updated_at_column CASCADE;
DROP FUNCTION IF EXISTS update_cart_total CASCADE;
DROP FUNCTION IF EXISTS sync_tenant_customer_stats CASCADE;

-- ------------------------------------------------------------------------------
-- 3. UPDATED_AT TRIGGER FUNCTION
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

-- Table: users (lat.nexofood.api.modules.identity.domain.User)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(20),
    system_role VARCHAR(50) NOT NULL DEFAULT 'USER'
        CONSTRAINT chk_users_system_role CHECK (system_role IN ('SUPERADMIN', 'USER')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table: customer_addresses (lat.nexofood.api.modules.identity.domain.CustomerAddress)
CREATE TABLE customer_addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(50) DEFAULT 'Casa',
    address_line TEXT NOT NULL,
    reference TEXT,
    location geometry(Point, 4326) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customer_addresses_user_id ON customer_addresses(user_id);
CREATE INDEX idx_customer_addresses_location ON customer_addresses USING GIST(location);

CREATE TRIGGER trg_customer_addresses_updated_at
BEFORE UPDATE ON customer_addresses
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table: refresh_tokens (lat.nexofood.api.modules.identity.domain.RefreshToken)
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token TEXT NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expiry_date TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);

CREATE TRIGGER trg_refresh_tokens_updated_at
BEFORE UPDATE ON refresh_tokens
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- 5. MODULE: SUBSCRIPTION
-- ==============================================================================

-- Table: subscription_plans (lat.nexofood.api.modules.subscription.domain.SubscriptionPlan)
CREATE TABLE subscription_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    billing_cycle_days INTEGER NOT NULL DEFAULT 30,
    max_products INTEGER,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER trg_subscription_plans_updated_at
BEFORE UPDATE ON subscription_plans
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table: subscriptions (lat.nexofood.api.modules.subscription.domain.Subscription)
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL REFERENCES subscription_plans(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'TRIAL'
        CONSTRAINT chk_subscriptions_status CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED')),
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ NOT NULL,
    mp_preapproval_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX idx_subscriptions_plan_id ON subscriptions(plan_id);

CREATE TRIGGER trg_subscriptions_updated_at
BEFORE UPDATE ON subscriptions
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- 6. MODULE: STORE
-- ==============================================================================

-- Table: tenants (lat.nexofood.api.modules.store.domain.Tenant)
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL UNIQUE REFERENCES subscriptions(id) ON DELETE RESTRICT,
    owner_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    logo_url TEXT,
    banner_url TEXT,
    phone VARCHAR(20),
    address TEXT,
    location geometry(Point, 4326),
    delivery_radius_km NUMERIC(5, 2) DEFAULT 5.00,
    default_delivery_fee NUMERIC(10, 2) DEFAULT 0.00,
    mp_access_token TEXT,
    mp_public_key TEXT,
    mp_refresh_token TEXT,
    mp_user_id VARCHAR(100),
    mp_connected_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenants_slug ON tenants(slug);
CREATE INDEX idx_tenants_owner_id ON tenants(owner_id);
CREATE INDEX idx_tenants_subscription_id ON tenants(subscription_id);
CREATE INDEX idx_tenants_location ON tenants USING GIST(location);

CREATE TRIGGER trg_tenants_updated_at
BEFORE UPDATE ON tenants
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table: tenant_members (lat.nexofood.api.modules.store.domain.TenantMember)
CREATE TABLE tenant_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL
        CONSTRAINT chk_tenant_members_role CHECK (role IN ('OWNER', 'ADMIN', 'COCINERO', 'REPARTIDOR')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_members_tenant_user UNIQUE (tenant_id, user_id)
);

CREATE INDEX idx_tenant_members_tenant_id ON tenant_members(tenant_id);
CREATE INDEX idx_tenant_members_user_id ON tenant_members(user_id);

CREATE TRIGGER trg_tenant_members_updated_at
BEFORE UPDATE ON tenant_members
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table: tenant_customers (lat.nexofood.api.modules.store.domain.TenantCustomer)
CREATE TABLE tenant_customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    loyalty_points INTEGER NOT NULL DEFAULT 0,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,
    total_orders INTEGER NOT NULL DEFAULT 0,
    first_order_at TIMESTAMPTZ,
    last_order_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_customer UNIQUE (tenant_id, user_id)
);

CREATE INDEX idx_tenant_customers_tenant_id ON tenant_customers(tenant_id);
CREATE INDEX idx_tenant_customers_user_id ON tenant_customers(user_id);

CREATE TRIGGER trg_tenant_customers_updated_at
BEFORE UPDATE ON tenant_customers
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- 7. MODULE: CATALOG
-- ==============================================================================

-- Table: categories (lat.nexofood.api.modules.catalog.domain.Category)
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_categories_tenant_id ON categories(tenant_id);

CREATE TRIGGER trg_categories_updated_at
BEFORE UPDATE ON categories
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table: products (lat.nexofood.api.modules.catalog.domain.Product)
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    image_url TEXT,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_products_tenant_id ON products(tenant_id);
CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_products_tenant_available ON products(tenant_id, is_available);

CREATE TRIGGER trg_products_updated_at
BEFORE UPDATE ON products
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table: product_prices (lat.nexofood.api.modules.catalog.domain.ProductPrice)
CREATE TABLE product_prices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    is_base BOOLEAN NOT NULL DEFAULT FALSE,
    discount_percentage NUMERIC(5, 2),
    start_date DATE,
    end_date DATE,
    start_time TIME,
    end_time TIME,
    days_of_week VARCHAR(100),
    priority INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_prices_product_id ON product_prices(product_id);
CREATE INDEX idx_product_prices_product_active ON product_prices(product_id, is_active);
CREATE INDEX idx_product_prices_product_base ON product_prices(product_id, is_base);
CREATE INDEX idx_product_prices_priority ON product_prices(priority DESC);

CREATE TRIGGER trg_product_prices_updated_at
BEFORE UPDATE ON product_prices
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- 8. MODULE: CART
-- ==============================================================================

-- Table: carts (lat.nexofood.api.modules.cart.domain.Cart)
CREATE TABLE carts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES tenant_customers(id) ON DELETE CASCADE,
    total NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_carts_tenant_customer UNIQUE (tenant_id, customer_id)
);

CREATE INDEX idx_carts_tenant_id ON carts(tenant_id);
CREATE INDEX idx_carts_customer_id ON carts(customer_id);

CREATE TRIGGER trg_carts_updated_at
BEFORE UPDATE ON carts
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table: cart_items (lat.nexofood.api.modules.cart.domain.CartItem)
CREATE TABLE cart_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_price NUMERIC(10, 2) NOT NULL,
    subtotal NUMERIC(10, 2) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id);
CREATE INDEX idx_cart_items_product_id ON cart_items(product_id);

CREATE TRIGGER trg_cart_items_updated_at
BEFORE UPDATE ON cart_items
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- 9. MODULE: ORDER
-- ==============================================================================

-- Table: orders (lat.nexofood.api.modules.order.domain.Order)
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES tenant_customers(id) ON DELETE RESTRICT,
    delivery_staff_id UUID REFERENCES users(id) ON DELETE SET NULL,
    order_number VARCHAR(20) NOT NULL,
    delivery_type VARCHAR(50) NOT NULL DEFAULT 'DELIVERY'
        CONSTRAINT chk_orders_delivery_type CHECK (delivery_type IN ('DELIVERY', 'TAKEAWAY', 'DINE_IN')),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDIENTE'
        CONSTRAINT chk_orders_status CHECK (status IN ('PENDIENTE', 'EN_PREPARACION', 'LISTO_PARA_ENTREGA', 'EN_CAMINO', 'ENTREGADO', 'CANCELADO')),
    delivery_address TEXT,
    delivery_location geometry(Point, 4326),
    subtotal NUMERIC(10, 2) NOT NULL,
    delivery_fee NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    total NUMERIC(10, 2) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_tenant_id ON orders(tenant_id);
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_delivery_staff_id ON orders(delivery_staff_id);
CREATE INDEX idx_orders_tenant_status ON orders(tenant_id, status);
CREATE INDEX idx_orders_tenant_number ON orders(tenant_id, order_number);
CREATE INDEX idx_orders_delivery_location ON orders USING GIST(delivery_location);

CREATE TRIGGER trg_orders_updated_at
BEFORE UPDATE ON orders
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table: order_items (lat.nexofood.api.modules.order.domain.OrderItem)
CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id) ON DELETE SET NULL,
    product_name VARCHAR(150) NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL,
    quantity INTEGER NOT NULL,
    subtotal NUMERIC(10, 2) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);

CREATE TRIGGER trg_order_items_updated_at
BEFORE UPDATE ON order_items
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- 10. MODULE: PAYMENT
-- ==============================================================================

-- Table: payments (lat.nexofood.api.modules.payment.domain.Payment)
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    mp_payment_id VARCHAR(100),
    mp_preference_id VARCHAR(100),
    payment_method VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING'
        CONSTRAINT chk_payments_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REFUNDED')),
    amount NUMERIC(10, 2) NOT NULL,
    raw_response JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_tenant_id ON payments(tenant_id);
CREATE INDEX idx_payments_mp_payment_id ON payments(mp_payment_id);
CREATE INDEX idx_payments_tenant_status ON payments(tenant_id, status);

CREATE TRIGGER trg_payments_updated_at
BEFORE UPDATE ON payments
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- 11. MODULE: INVENTORY
-- ==============================================================================

-- Table: inventory_items (lat.nexofood.api.modules.inventory.domain.InventoryItem)
CREATE TABLE inventory_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    sku VARCHAR(50),
    description TEXT,
    category VARCHAR(100),
    unit VARCHAR(20) NOT NULL DEFAULT 'UNIT'
        CONSTRAINT chk_inventory_items_unit CHECK (unit IN ('UNIT', 'KG')),
    cost_price NUMERIC(10, 2) DEFAULT 0.00,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inventory_items_tenant_id ON inventory_items(tenant_id);
CREATE INDEX idx_inventory_items_tenant_sku ON inventory_items(tenant_id, sku);

CREATE TRIGGER trg_inventory_items_updated_at
BEFORE UPDATE ON inventory_items
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table: inventory_stocks (lat.nexofood.api.modules.inventory.domain.InventoryStock)
CREATE TABLE inventory_stocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    inventory_item_id UUID NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
    quantity NUMERIC(12, 3) NOT NULL DEFAULT 0.000,
    reserved_quantity NUMERIC(12, 3) NOT NULL DEFAULT 0.000,
    minimum_stock NUMERIC(12, 3) NOT NULL DEFAULT 0.000,
    maximum_stock NUMERIC(12, 3),
    location VARCHAR(150),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inventory_stocks_tenant_item UNIQUE (tenant_id, inventory_item_id)
);

CREATE INDEX idx_inventory_stocks_tenant_id ON inventory_stocks(tenant_id);
CREATE INDEX idx_inventory_stocks_item_id ON inventory_stocks(inventory_item_id);

CREATE TRIGGER trg_inventory_stocks_updated_at
BEFORE UPDATE ON inventory_stocks
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table: inventory_movements (lat.nexofood.api.modules.inventory.domain.InventoryMovement)
CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    inventory_stock_id UUID NOT NULL REFERENCES inventory_stocks(id) ON DELETE CASCADE,
    inventory_item_id UUID NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
    movement_type VARCHAR(50) NOT NULL
        CONSTRAINT chk_inventory_movements_type CHECK (movement_type IN ('ENTRY', 'EXIT', 'ADJUSTMENT')),
    reason VARCHAR(50) NOT NULL
        CONSTRAINT chk_inventory_movements_reason CHECK (reason IN (
            'PURCHASE', 'INITIAL_STOCK', 'CUSTOMER_RETURN', 'TRANSFER_IN',
            'KITCHEN_CONSUMPTION_OR_WASTE', 'EXPIRED_OR_SPOILAGE', 'DAMAGED_OR_LOSS',
            'SALE', 'INTERNAL_CONSUMPTION', 'SUPPLIER_RETURN', 'TRANSFER_OUT',
            'PHYSICAL_COUNT', 'CORRECTION', 'OTHER'
        )),
    quantity NUMERIC(12, 3) NOT NULL,
    previous_quantity NUMERIC(12, 3) NOT NULL,
    new_quantity NUMERIC(12, 3) NOT NULL,
    performed_by_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    performed_by_name VARCHAR(150),
    reason_details TEXT NOT NULL,
    reference_id UUID,
    reference_type VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inventory_movements_tenant_id ON inventory_movements(tenant_id);
CREATE INDEX idx_inventory_movements_stock_id ON inventory_movements(inventory_stock_id);
CREATE INDEX idx_inventory_movements_item_id ON inventory_movements(inventory_item_id);
CREATE INDEX idx_inventory_movements_performed_by ON inventory_movements(performed_by_id);
CREATE INDEX idx_inventory_movements_created_at ON inventory_movements(tenant_id, created_at DESC);
CREATE INDEX idx_inventory_movements_reference ON inventory_movements(reference_id, reference_type);

CREATE TRIGGER trg_inventory_movements_updated_at
BEFORE UPDATE ON inventory_movements
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- 12. MEJORAS DE SEGURIDAD, INTEGRIDAD Y NORMALIZACIÓN
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- FASE 1: SEGURIDAD CRÍTICA
-- ------------------------------------------------------------------------------

-- 1.1 Encriptación de credenciales Mercado Pago (AES-256-GCM desde la aplicación)
-- Las columnas cambian de TEXT a BYTEA para almacenar el ciphertext binario.
ALTER TABLE tenants RENAME COLUMN mp_access_token TO mp_access_token_enc;
ALTER TABLE tenants ALTER COLUMN mp_access_token_enc TYPE BYTEA USING NULL;

ALTER TABLE tenants RENAME COLUMN mp_refresh_token TO mp_refresh_token_enc;
ALTER TABLE tenants ALTER COLUMN mp_refresh_token_enc TYPE BYTEA USING NULL;

ALTER TABLE tenants RENAME COLUMN mp_public_key TO mp_public_key_enc;
ALTER TABLE tenants ALTER COLUMN mp_public_key_enc TYPE BYTEA USING NULL;

-- 1.2 Hash de refresh tokens
-- Se almacena solo el hash SHA-256 del token, nunca el token en texto plano.
ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS token;
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64);
DROP INDEX IF EXISTS idx_refresh_tokens_token;
CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);

-- Hacer NOT NULL después de agregar (en producción con datos existentes se haría en 2 pasos)
ALTER TABLE refresh_tokens ALTER COLUMN token_hash SET NOT NULL;

-- 1.3 Raw response de pagos: la sanitización es responsabilidad de la aplicación (PaymentMapper).
-- No se requieren cambios de schema para este punto.

-- 1.4 Row Level Security (RLS) — Aislamiento multi-tenant a nivel de base de datos
-- NOTA: El rol de la aplicación (nexofood_app) NO debe ser superuser.
-- Los superusers bypasean RLS automáticamente.

ALTER TABLE tenant_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE carts ENABLE ROW LEVEL SECURITY;
ALTER TABLE cart_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_stocks ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_movements ENABLE ROW LEVEL SECURITY;

-- Políticas de aislamiento por tenant para tablas con tenant_id directo
CREATE POLICY tenant_isolation ON tenant_members
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));
CREATE POLICY tenant_isolation ON tenant_customers
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));
CREATE POLICY tenant_isolation ON categories
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));
CREATE POLICY tenant_isolation ON products
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));
CREATE POLICY tenant_isolation ON carts
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));
CREATE POLICY tenant_isolation ON orders
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));
CREATE POLICY tenant_isolation ON payments
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));
CREATE POLICY tenant_isolation ON inventory_items
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));
CREATE POLICY tenant_isolation ON inventory_stocks
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));
CREATE POLICY tenant_isolation ON inventory_movements
    USING (tenant_id::TEXT = current_setting('app.current_tenant_id', true));

-- Políticas para tablas sin tenant_id directo (heredan por JOIN con padre)
CREATE POLICY tenant_isolation ON cart_items
    USING (cart_id IN (SELECT id FROM carts));
CREATE POLICY tenant_isolation ON order_items
    USING (order_id IN (SELECT id FROM orders));

-- ------------------------------------------------------------------------------
-- FASE 2: INTEGRIDAD REFERENCIAL Y LÓGICA DE NEGOCIO
-- ------------------------------------------------------------------------------

-- 2.1 Corregir ON DELETE CASCADE destructivo
-- Cambiar de CASCADE a RESTRICT en identidades clave para preservar historial

-- tenant_customers.user_id: CASCADE → RESTRICT
ALTER TABLE tenant_customers
    DROP CONSTRAINT IF EXISTS tenant_customers_user_id_fkey;
ALTER TABLE tenant_customers
    ADD CONSTRAINT tenant_customers_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;

-- subscriptions.user_id: CASCADE → RESTRICT
ALTER TABLE subscriptions
    DROP CONSTRAINT IF EXISTS subscriptions_user_id_fkey;
ALTER TABLE subscriptions
    ADD CONSTRAINT subscriptions_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;

-- tenant_members.user_id: CASCADE → RESTRICT
ALTER TABLE tenant_members
    DROP CONSTRAINT IF EXISTS tenant_members_user_id_fkey;
ALTER TABLE tenant_members
    ADD CONSTRAINT tenant_members_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;

-- Soft delete: marcar usuarios como eliminados sin borrarlos físicamente
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- 2.2 Prevenir Cross-Tenant Leakage en cart_items y order_items
-- FK compuestas garantizan que el producto pertenezca al mismo tenant

-- Clave compuesta única en products
ALTER TABLE products
    ADD CONSTRAINT uk_products_id_tenant UNIQUE (id, tenant_id);

-- Agregar tenant_id a cart_items y FK compuesta
ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE cart_items ci
    SET tenant_id = c.tenant_id
    FROM carts c
    WHERE ci.cart_id = c.id
      AND ci.tenant_id IS NULL;
ALTER TABLE cart_items ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE cart_items
    DROP CONSTRAINT IF EXISTS cart_items_product_id_fkey;
ALTER TABLE cart_items
    ADD CONSTRAINT cart_items_product_tenant_fkey
        FOREIGN KEY (product_id, tenant_id) REFERENCES products(id, tenant_id);

-- Agregar tenant_id a order_items y FK compuesta
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE order_items oi
    SET tenant_id = o.tenant_id
    FROM orders o
    WHERE oi.order_id = o.id
      AND oi.tenant_id IS NULL;
ALTER TABLE order_items ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE order_items
    DROP CONSTRAINT IF EXISTS order_items_product_id_fkey;
ALTER TABLE order_items
    ADD CONSTRAINT order_items_product_tenant_fkey
        FOREIGN KEY (product_id, tenant_id) REFERENCES products(id, tenant_id);

-- 2.3 Unicidad de order_number por tenant
ALTER TABLE orders
    ADD CONSTRAINT uk_orders_tenant_number UNIQUE (tenant_id, order_number);

-- 2.4 CHECK: inventario no puede ser negativo
ALTER TABLE inventory_stocks
    ADD CONSTRAINT chk_inventory_stocks_quantity_non_negative
        CHECK (quantity >= 0);
ALTER TABLE inventory_stocks
    ADD CONSTRAINT chk_inventory_stocks_reserved_non_negative
        CHECK (reserved_quantity >= 0);

-- ------------------------------------------------------------------------------
-- FASE 3: NORMALIZACIÓN Y CONSISTENCIA
-- ------------------------------------------------------------------------------

-- 3.1 Columnas calculadas: subtotal como GENERATED ALWAYS

-- cart_items.subtotal
ALTER TABLE cart_items DROP COLUMN IF EXISTS subtotal;
ALTER TABLE cart_items
    ADD COLUMN subtotal NUMERIC(10, 2)
    GENERATED ALWAYS AS (quantity * unit_price) STORED;

-- order_items.subtotal
ALTER TABLE order_items DROP COLUMN IF EXISTS subtotal;
ALTER TABLE order_items
    ADD COLUMN subtotal NUMERIC(10, 2)
    GENERATED ALWAYS AS (quantity * unit_price) STORED;

-- orders.total = subtotal + delivery_fee
ALTER TABLE orders DROP COLUMN IF EXISTS total;
ALTER TABLE orders
    ADD COLUMN total NUMERIC(10, 2)
    GENERATED ALWAYS AS (subtotal + delivery_fee) STORED;

-- carts.total: no puede ser GENERATED (depende de otra tabla).
-- Se sincroniza mediante trigger.
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

-- 3.2 Trigger para mantener sincronizadas las estadísticas de tenant_customers
-- (total_orders, first_order_at, last_order_at) automáticamente

CREATE OR REPLACE FUNCTION sync_tenant_customer_stats()
RETURNS TRIGGER AS $$
DECLARE
    v_customer_id UUID;
BEGIN
    v_customer_id := COALESCE(NEW.customer_id, OLD.customer_id);

    UPDATE tenant_customers SET
        total_orders = (
            SELECT COUNT(*)
            FROM orders
            WHERE customer_id = v_customer_id
              AND status != 'CANCELADO'
        ),
        first_order_at = (
            SELECT MIN(created_at)
            FROM orders
            WHERE customer_id = v_customer_id
              AND status != 'CANCELADO'
        ),
        last_order_at = (
            SELECT MAX(created_at)
            FROM orders
            WHERE customer_id = v_customer_id
              AND status != 'CANCELADO'
        )
    WHERE id = v_customer_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_orders_sync_customer_stats
AFTER INSERT OR UPDATE OF status OR DELETE ON orders
FOR EACH ROW EXECUTE FUNCTION sync_tenant_customer_stats();

-- 3.3 days_of_week: de VARCHAR(100) a SMALLINT[] (ISO: 1=Lunes...7=Domingo)
-- La migración de datos existentes requiere un script específico según el
-- formato almacenado. Aquí se realiza el cambio de schema.
ALTER TABLE product_prices ADD COLUMN IF NOT EXISTS days_of_week_arr SMALLINT[];
ALTER TABLE product_prices DROP COLUMN IF EXISTS days_of_week;
ALTER TABLE product_prices RENAME COLUMN days_of_week_arr TO days_of_week;

ALTER TABLE product_prices
    ADD CONSTRAINT chk_product_prices_days_valid
        CHECK (days_of_week <@ ARRAY[1,2,3,4,5,6,7]::SMALLINT[]);

CREATE INDEX IF NOT EXISTS idx_product_prices_days
    ON product_prices USING GIN(days_of_week);

-- 3.4 CHECK de consistencia aritmética en inventory_movements
-- new_quantity debe ser coherente con previous_quantity + delta
ALTER TABLE inventory_movements
    ADD CONSTRAINT chk_inventory_movements_quantity_consistency
        CHECK (
            (movement_type = 'ENTRY'      AND new_quantity = previous_quantity + quantity) OR
            (movement_type = 'EXIT'       AND new_quantity = previous_quantity - quantity) OR
            (movement_type = 'ADJUSTMENT')
        );

-- ------------------------------------------------------------------------------
-- FASE 4: MEJORAS OPCIONALES
-- ------------------------------------------------------------------------------

-- 4.1 Referencia opcional de dirección guardada en orders
-- Permite rastrear qué dirección guardada originó cada pedido
ALTER TABLE orders ADD COLUMN IF NOT EXISTS address_id UUID;
ALTER TABLE orders
    ADD CONSTRAINT fk_orders_address
        FOREIGN KEY (address_id) REFERENCES customer_addresses(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_orders_address_id ON orders(address_id);

-- Nota sobre 4.2 (owner_id UNIQUE en tenants):
-- Se mantiene la restricción existente. Revisión futura si el modelo
-- de negocio necesita que un usuario sea owner de múltiples tenants.

-- ------------------------------------------------------------------------------
-- CONFIGURACIÓN DE VARIABLE DE APLICACIÓN PARA RLS
-- Ejecutar esto en la configuración inicial de PostgreSQL:
-- ALTER DATABASE nexofood_db SET app.current_tenant_id = '';
-- ------------------------------------------------------------------------------
