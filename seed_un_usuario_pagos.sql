-- ==============================================================================
-- NEXOFOOD API - Seed: 1 Único Usuario de Prueba para Endpoints de Pagos
-- Archivo: seed_un_usuario_pagos.sql
-- ==============================================================================

BEGIN;

-- 1. LIMPIEZA DE DATOS PREVIOS
DELETE FROM subscription_payments;
DELETE FROM payments;
DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM cart_items;
DELETE FROM carts;
DELETE FROM product_prices;
DELETE FROM products;
DELETE FROM categories;
DELETE FROM taxes;
DELETE FROM tenant_customers;
DELETE FROM tenant_members;
DELETE FROM tenants;
DELETE FROM subscriptions;
DELETE FROM subscription_plans;
DELETE FROM refresh_tokens;
DELETE FROM customer_addresses;
DELETE FROM users;

-- Establecer tenant en sesión para políticas RLS
SET LOCAL app.current_tenant_id = '11111111-1111-1111-1111-111111111111';

-- 2. EL ÚNICO USUARIO DE PRUEBA (SUPERADMIN y Dueño del Restaurante)
-- Credenciales:
--   Email:    admin@nexofood.lat
--   Password: Password123!
--   BCrypt:   $2a$10$eP2e.KYoaoI9VNmTm2vUA.VTnfVmokRp8zwgYcbTiQroWhO.Y6DkG
INSERT INTO users (
    id, email, password_hash, full_name, phone, system_role, is_active, token_version, failed_login_attempts
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin@nexofood.lat',
    '$2a$10$eP2e.KYoaoI9VNmTm2vUA.VTnfVmokRp8zwgYcbTiQroWhO.Y6DkG',
    'Usuario Pruebas Pagos',
    '+51900000001',
    'SUPERADMIN',
    true,
    0,
    0
);

-- 3. DIRECCIÓN DE ENTREGA DEL USUARIO (Para Checkout Pro Delivery)
INSERT INTO customer_addresses (
    id, user_id, title, address_line, reference, location
) VALUES (
    'a0000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'Oficina Principal',
    'Av. Larco 456, Miraflores',
    '[TEST] Frente al parque',
    ST_SetSRID(ST_MakePoint(-77.0298, -12.1221), 4326)
);

-- 4. PLAN DE SUSCRIPCIÓN SAAS
INSERT INTO subscription_plans (
    id, name, price, billing_cycle_days, max_products, is_active
) VALUES (
    'c0000000-0000-0000-0000-000000000001',
    '[TEST] Plan Pro Restaurante',
    149.00,
    30,
    100,
    true
);

-- 5. SUSCRIPCIÓN ACTIVA VINCULADA AL USUARIO
INSERT INTO subscriptions (
    id, plan_id, user_id, status, start_date, end_date, mp_preapproval_id
) VALUES (
    'd0000000-0000-0000-0000-000000000001',
    'c0000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'ACTIVE',
    NOW() - INTERVAL '5 days',
    NOW() + INTERVAL '25 days',
    '2c9380848e8c8122018e90f0a1b20001'
);

-- 6. HISTORIAL DE PAGO DE SUSCRIPCIÓN (Para GET /api/v1/subscriptions/me/payments)
INSERT INTO subscription_payments (
    id, subscription_id, mp_authorized_payment_id, mp_payment_id, status, amount, currency, paid_at, period_end, raw_response
) VALUES (
    'f0000000-0000-0000-0000-000000000001',
    'd0000000-0000-0000-0000-000000000001',
    'AUTH-PAY-TEST-001',
    'MP-SUB-99990001',
    'APPROVED',
    149.00,
    'PEN',
    NOW() - INTERVAL '5 days',
    NOW() + INTERVAL '25 days',
    '{"id": 99990001, "status": "approved", "transaction_amount": 149.00}'::JSONB
);

-- 7. RESTAURANTE (TENANT) CUYO OWNER ES EL USUARIO DE PRUEBA
INSERT INTO tenants (
    id, subscription_id, owner_id, name, slug, address, location, delivery_radius_km, default_delivery_fee, mp_access_token_enc, mp_public_key_enc, mp_refresh_token_enc, mp_user_id, is_active
) VALUES (
    '11111111-1111-1111-1111-111111111111',
    'd0000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'Pizzería Bella Napoli',
    'bella-napoli',
    'Av. Benavides 1500, Miraflores',
    ST_SetSRID(ST_MakePoint(-77.0250, -12.1250), 4326),
    10.00,
    5.00,
    NULL,
    NULL,
    NULL,
    NULL,
    true
);

-- 8. MIEMBRO DEL STAFF (OWNER)
INSERT INTO tenant_members (
    id, tenant_id, user_id, role, is_active
) VALUES (
    'e0000000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000001',
    'OWNER',
    true
);

-- 9. CLIENTE DEL RESTAURANTE (El usuario también actúa como cliente de la tienda)
INSERT INTO tenant_customers (
    tenant_id, user_id, loyalty_points, is_blocked, notes, total_orders
) VALUES (
    '11111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000001',
    10,
    false,
    '[TEST] Cliente para pruebas de checkout',
    1
);

-- 10. IMPUESTO (IGV 18%)
INSERT INTO taxes (
    id, tenant_id, name, rate, is_inclusive, is_active
) VALUES (
    '70000000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    'IGV',
    18.00,
    true,
    true
);

-- 11. CATEGORÍA
INSERT INTO categories (
    id, tenant_id, name, description, sort_order, is_active
) VALUES (
    '10000000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    'Pizzas Artesanales',
    '[TEST] Masa madre al horno',
    1,
    true
);

-- 12. PRODUCTO PARA CHECKOUT
INSERT INTO products (
    id, tenant_id, category_id, name, description, is_available
) VALUES (
    '11000000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    '10000000-0000-0000-0000-000000000001',
    'Pizza Margherita',
    '[TEST] Tomate San Marzano, mozzarella fior di latte',
    true
);

-- 13. PRECIO DEL PRODUCTO
INSERT INTO product_prices (
    id, product_id, name, price, is_base, discount_percentage, days_of_week, priority, is_active
) VALUES (
    '12000000-0000-0000-0000-000000000001',
    '11000000-0000-0000-0000-000000000001',
    '[TEST] Precio Base Margherita',
    38.00,
    true,
    0.00,
    NULL,
    0,
    true
);

-- 14. CARRITO ACTIVO PARA PROBAR CHECKOUT (POST /api/v1/store/bella-napoli/orders/checkout)
INSERT INTO carts (
    id, tenant_id, customer_id, total, notes
) VALUES (
    '30000000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000001',
    0.00,
    '[TEST] Carrito activo para checkout'
);

-- Item en carrito (subtotal es GENERATED ALWAYS AS, trigger sincroniza total en carts = 38.00)
INSERT INTO cart_items (
    id, cart_id, tenant_id, product_id, quantity, unit_price, notes
) VALUES (
    '31000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    '11000000-0000-0000-0000-000000000001',
    1,
    38.00,
    '[TEST] 1 Pizza Margherita'
);

-- 15. ORDEN DE PRUEBA
INSERT INTO orders (
    id, tenant_id, customer_id, delivery_staff_id, address_id,
    order_number, ruc, delivery_type, status,
    delivery_address, delivery_location,
    subtotal, tax_total, delivery_fee, notes
) VALUES (
    '40000000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000001',
    NULL,
    'a0000000-0000-0000-0000-000000000001',
    'ORD-001',
    '20123456789',
    'DELIVERY',
    'PENDIENTE',
    'Av. Larco 456, Miraflores',
    ST_SetSRID(ST_MakePoint(-77.0298, -12.1221), 4326),
    38.00,
    0.00,
    5.00,
    '[TEST] Pedido inicial para pruebas de pago'
);

-- Item de la orden (subtotal es GENERATED ALWAYS AS)
INSERT INTO order_items (
    id, order_id, tenant_id, product_id, price_id, tax_id,
    product_name, unit_price, quantity, notes
) VALUES (
    '41000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    '11000000-0000-0000-0000-000000000001',
    '12000000-0000-0000-0000-000000000001',
    NULL,
    'Pizza Margherita',
    38.00,
    1,
    '[TEST] Item orden pagada'
);

-- 16. PAGO APROBADO ASOCIADO A LA ORDEN
INSERT INTO payments (
    id, tenant_id, customer_id, order_id,
    mp_order_id, mp_payment_id, payment_method, status, status_detail,
    amount, currency, checkout_snapshot, raw_response, approved_at
) VALUES (
    '42000000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000001',
    'ORD01JKM9P5V7Z8Q',
    'MP-TEST-99887711',
    'credit_card',
    'APPROVED',
    'accredited',
    43.00,
    'PEN',
    '{"total": 43.00, "items": [{"quantity": 1, "subtotal": 38.00, "unitPrice": 38.00, "productId": "11000000-0000-0000-0000-000000000001", "productName": "Pizza Margherita"}], "subtotal": 38.00, "taxTotal": 0.00, "tenantId": "11111111-1111-1111-1111-111111111111", "deliveryFee": 5.00, "deliveryType": "DELIVERY", "customerUserId": "00000000-0000-0000-0000-000000000001", "customerAddressId": "a0000000-0000-0000-0000-000000000001"}'::JSONB,
    '{"id": 99887711, "status": "approved", "status_detail": "accredited", "currency_id": "PEN", "payment_method_id": "visa", "transaction_amount": 43.00}'::JSONB,
    NOW()
);

COMMIT;
