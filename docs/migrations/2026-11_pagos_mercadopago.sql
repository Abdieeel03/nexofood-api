-- ==============================================================================
-- MIGRACIÓN: Integración de Pagos con Mercado Pago (Checkout Pro Orders API + Preapprovals)
-- Fecha: 2026-11
-- ==============================================================================

-- 1. TENANTS: expiración del token OAuth
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS mp_token_expires_at TIMESTAMPTZ;

-- 2. SUBSCRIPTIONS: estado PENDING, estado de preapproval de MP y cancel_at_period_end
ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS chk_subscriptions_status;
ALTER TABLE subscriptions ADD CONSTRAINT chk_subscriptions_status
    CHECK (status IN ('PENDING', 'TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED'));

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS mp_preapproval_status VARCHAR(50),
    ADD COLUMN IF NOT EXISTS cancel_at_period_end  BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_subscriptions_mp_preapproval_id
    ON subscriptions(mp_preapproval_id)
    WHERE mp_preapproval_id IS NOT NULL;

-- 3. SUBSCRIPTION_PAYMENTS: historial de cobros recurrentes (idempotencia por authorized_payment)
CREATE TABLE IF NOT EXISTS subscription_payments (
    id                        UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id           UUID           NOT NULL REFERENCES subscriptions(id) ON DELETE RESTRICT,
    mp_authorized_payment_id  VARCHAR(100)   NOT NULL UNIQUE,
    mp_payment_id             VARCHAR(100),
    status                    VARCHAR(50)    NOT NULL
        CONSTRAINT chk_subscription_payments_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REFUNDED')),
    amount                    NUMERIC(10, 2) NOT NULL,
    currency                  VARCHAR(3)     NOT NULL DEFAULT 'PEN',
    paid_at                   TIMESTAMPTZ,
    period_end                TIMESTAMPTZ,
    raw_response              JSONB,
    created_at                TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_subscription_payments_subscription_id
    ON subscription_payments(subscription_id);

DROP TRIGGER IF EXISTS trg_subscription_payments_updated_at ON subscription_payments;
CREATE TRIGGER trg_subscription_payments_updated_at
BEFORE UPDATE ON subscription_payments
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 4. PAYMENTS: se recrea para soportar Checkout Pro vía Orders API (el pago existe antes que la orden)
DROP TABLE IF EXISTS payments CASCADE;

CREATE TABLE payments (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID           NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    customer_id       UUID           NOT NULL,
    order_id          UUID           UNIQUE REFERENCES orders(id) ON DELETE SET NULL,
    mp_order_id       VARCHAR(100)   UNIQUE,
    mp_payment_id     VARCHAR(100),
    checkout_url      TEXT,
    payment_method    VARCHAR(50),
    status            VARCHAR(50)    NOT NULL DEFAULT 'PENDING'
        CONSTRAINT chk_payments_status CHECK (status IN (
            'PENDING', 'IN_PROCESS', 'APPROVED', 'REJECTED', 'CANCELED',
            'EXPIRED', 'REFUND_PENDING', 'REFUNDED', 'CHARGED_BACK'
        )),
    status_detail     VARCHAR(100),
    amount            NUMERIC(10, 2) NOT NULL,
    currency          VARCHAR(3)     NOT NULL DEFAULT 'PEN',
    checkout_snapshot JSONB          NOT NULL,
    raw_response      JSONB,
    expires_at        TIMESTAMPTZ,
    approved_at       TIMESTAMPTZ,
    refunded_at       TIMESTAMPTZ,
    refund_reason     TEXT,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payments_tenant_customer_fkey
        FOREIGN KEY (tenant_id, customer_id) REFERENCES tenant_customers(tenant_id, user_id) ON DELETE RESTRICT
);

CREATE INDEX idx_payments_order_id       ON payments(order_id);
CREATE INDEX idx_payments_tenant_id      ON payments(tenant_id);
CREATE INDEX idx_payments_mp_order_id    ON payments(mp_order_id);
CREATE INDEX idx_payments_mp_payment_id  ON payments(mp_payment_id);
CREATE INDEX idx_payments_tenant_status  ON payments(tenant_id, status);
CREATE INDEX idx_payments_customer       ON payments(tenant_id, customer_id);
CREATE INDEX idx_payments_status_created ON payments(status, created_at);

CREATE TRIGGER trg_payments_updated_at
BEFORE UPDATE ON payments
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON payments
    USING (
        tenant_id::TEXT = current_setting('app.current_tenant_id', true)
        OR COALESCE(current_setting('app.current_tenant_id', true), '') = ''
    );

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nexofood_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON payments, subscription_payments TO nexofood_app;
    END IF;
END
$$;
