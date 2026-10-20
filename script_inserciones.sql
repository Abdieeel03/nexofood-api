-- ==============================================================================
-- NEXOFOOD API - Script de Inserciones y Pruebas de Desarrollo
-- Archivo: script_inserciones.sql
-- Propósito: Poblado de datos de prueba para 2 restaurantes distintos (Multi-Tenant)
-- y suite de consultas para verificar RLS, triggers, columnas generadas y constraints.
-- ==============================================================================

BEGIN;

-- ------------------------------------------------------------------------------
-- 1. LIMPIEZA DE DATOS PREVIOS DE PRUEBA
-- ------------------------------------------------------------------------------
DELETE FROM inventory_movements WHERE reason_details LIKE '[TEST]%';
DELETE FROM inventory_stocks WHERE location LIKE '[TEST]%';
DELETE FROM inventory_items WHERE name LIKE '[TEST]%';
DELETE FROM payments WHERE mp_payment_id LIKE 'MP-TEST-%';
DELETE FROM order_items WHERE notes LIKE '[TEST]%';
DELETE FROM orders WHERE notes LIKE '[TEST]%';
DELETE FROM cart_items WHERE notes LIKE '[TEST]%';
DELETE FROM carts WHERE notes LIKE '[TEST]%';
DELETE FROM product_prices WHERE name LIKE '[TEST]%';
DELETE FROM products WHERE description LIKE '[TEST]%';
DELETE FROM categories WHERE description LIKE '[TEST]%';
DELETE FROM tenant_customers WHERE notes LIKE '[TEST]%';
DELETE FROM tenant_members WHERE tenant_id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222');
DELETE FROM tenants WHERE id IN ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222');
DELETE FROM subscriptions WHERE mp_preapproval_id LIKE 'SUB-TEST-%';
DELETE FROM subscription_plans WHERE name LIKE '[TEST]%';
DELETE FROM customer_addresses WHERE reference LIKE '[TEST]%';
DELETE FROM refresh_tokens WHERE token_hash LIKE 'hash_test_%' OR token_hash = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855';
DELETE FROM users WHERE email LIKE '%@test.nexofood.lat';

-- ==============================================================================
-- 2. USUARIOS (IDENTITY)
-- ==============================================================================
-- Contraseña simulada para todos: '$2a$10$hashedpassword'
INSERT INTO users (id, email, password_hash, full_name, phone, system_role, is_active) VALUES
('00000000-0000-0000-0000-000000000001', 'admin@test.nexofood.lat',     '$2a$10$hashedpassword', 'Administrador Global', '+51900000001', 'SUPERADMIN', true),
('00000000-0000-0000-0000-000000000002', 'mario@test.nexofood.lat',     '$2a$10$hashedpassword', 'Mario Rossi (Dueño T1)', '+51900000002', 'USER', true),
('00000000-0000-0000-0000-000000000003', 'bob@test.nexofood.lat',       '$2a$10$hashedpassword', 'Bob Burger (Dueño T2)', '+51900000003', 'USER', true),
('00000000-0000-0000-0000-000000000004', 'carlos.cliente@test.nexofood.lat', '$2a$10$hashedpassword', 'Carlos Cliente', '+51900000004', 'USER', true),
('00000000-0000-0000-0000-000000000005', 'ana.cliente@test.nexofood.lat',    '$2a$10$hashedpassword', 'Ana Cliente',    '+51900000005', 'USER', true),
('00000000-0000-0000-0000-000000000006', 'pedro.repartidor@test.nexofood.lat','$2a$10$hashedpassword', 'Pedro Repartidor', '+51900000006', 'USER', true);

-- Direcciones de entrega (PostGIS Point: longitud, latitud)
INSERT INTO customer_addresses (id, user_id, title, address_line, reference, location) VALUES
('a0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000004', 'Casa', 'Av. Larco 456, Miraflores', '[TEST] Frente al parque', ST_SetSRID(ST_MakePoint(-77.0298, -12.1221), 4326)),
('a0000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005', 'Oficina', 'Calle San Martín 123, Miraflores', '[TEST] Piso 4', ST_SetSRID(ST_MakePoint(-77.0315, -12.1205), 4326));

-- Refresh token con hash SHA-256 (64 hex chars)
INSERT INTO refresh_tokens (id, token_hash, user_id, expiry_date, revoked) VALUES
('b0000000-0000-0000-0000-000000000001', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', '00000000-0000-0000-0000-000000000004', NOW() + INTERVAL '7 days', false);

-- ==============================================================================
-- 3. PLANES Y SUSCRIPCIONES
-- ==============================================================================
INSERT INTO subscription_plans (id, name, price, billing_cycle_days, max_products, is_active) VALUES
('c0000000-0000-0000-0000-000000000001', '[TEST] Plan Premium Restaurante', 149.00, 30, 100, true);

INSERT INTO subscriptions (id, plan_id, user_id, status, start_date, end_date, mp_preapproval_id) VALUES
('d0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'ACTIVE', NOW() - INTERVAL '5 days', NOW() + INTERVAL '25 days', 'SUB-TEST-MP-001'),
('d0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', 'ACTIVE', NOW() - INTERVAL '10 days', NOW() + INTERVAL '20 days', 'SUB-TEST-MP-002');

-- ==============================================================================
-- 4. TENANTS (RESTAURANTES)
-- ==============================================================================
-- Credenciales MP en BYTEA simuladas (cifrado AES-256-GCM a nivel de aplicación)
INSERT INTO tenants (id, subscription_id, owner_id, name, slug, address, location, delivery_radius_km, default_delivery_fee, mp_access_token_enc, mp_public_key_enc, mp_refresh_token_enc, mp_user_id, is_active) VALUES
('11111111-1111-1111-1111-111111111111', 'd0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'Pizzería Bella Napoli', 'bella-napoli', 'Av. Benavides 1500, Miraflores', ST_SetSRID(ST_MakePoint(-77.0250, -12.1250), 4326), 6.00, 5.00, decode('0102030405060708090a0b0c0d0e0f10', 'hex'), decode('0102030405060708090a0b0c0d0e0f10', 'hex'), decode('0102030405060708090a0b0c0d0e0f10', 'hex'), 'MP-USER-NAPOLI', true),
('22222222-2222-2222-2222-222222222222', 'd0000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', 'Burger House Gourmet',  'burger-house', 'Calle Schell 340, Miraflores',   ST_SetSRID(ST_MakePoint(-77.0280, -12.1210), 4326), 5.00, 4.50, decode('1112131415161718191a1b1c1d1e1f20', 'hex'), decode('1112131415161718191a1b1c1d1e1f20', 'hex'), decode('1112131415161718191a1b1c1d1e1f20', 'hex'), 'MP-USER-BURGER', true);

-- Miembros de staff de cada restaurante
INSERT INTO tenant_members (id, tenant_id, user_id, role, is_active) VALUES
('e0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000002', 'OWNER', true),
('e0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000006', 'REPARTIDOR', true),
('e0000000-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000003', 'OWNER', true);

-- Clientes asociados a cada restaurante
INSERT INTO tenant_customers (tenant_id, user_id, loyalty_points, is_blocked, notes, total_orders) VALUES
('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000004', 10, false, '[TEST] Cliente frecuente de pizzas', 0),
('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000004', 0,  false, '[TEST] Carlos en Burger House', 0),
('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000005', 5,  false, '[TEST] Ana en Bella Napoli', 0);

-- ==============================================================================
-- 5. CATÁLOGO: CATEGORÍAS, PRODUCTOS Y PRECIOS
-- ==============================================================================
-- Categorías
INSERT INTO categories (id, tenant_id, name, description, sort_order, is_active) VALUES
('10000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Pizzas Artesanales', '[TEST] Masa madre al horno', 1, true),
('10000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Bebidas Italianas',   '[TEST] Refrescos y cervezas', 2, true),
('20000000-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'Burgers Smash',       '[TEST] Carne Angus smash', 1, true);

-- Productos (con tenant_id para llave compuesta)
INSERT INTO products (id, tenant_id, category_id, name, description, is_available) VALUES
('11000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '10000000-0000-0000-0000-000000000001', 'Pizza Margherita', '[TEST] Tomate San Marzano, mozzarella fior di latte', true),
('11000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', '10000000-0000-0000-0000-000000000001', 'Pizza Pepperoni',  '[TEST] Doble pepperoni americano crocante', true),
('11000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', '10000000-0000-0000-0000-000000000002', 'Cerveza Peroni',   '[TEST] Botella 330ml importada', true),
('21000000-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', '20000000-0000-0000-0000-000000000001', 'Doble Cheese Smash', '[TEST] Doble carne con queso cheddar fundido', true);

-- Precios y promociones (days_of_week en SMALLINT[] con días ISO: 1=Lun, 5=Vie, 6=Sáb, 7=Dom)
INSERT INTO product_prices (id, product_id, name, price, is_base, discount_percentage, days_of_week, priority, is_active) VALUES
('12000000-0000-0000-0000-000000000001', '11000000-0000-0000-0000-000000000001', '[TEST] Precio Base Margherita', 38.00, true,  0.00, NULL, 0, true),
('12000000-0000-0000-0000-000000000002', '11000000-0000-0000-0000-000000000001', '[TEST] Happy Hour Viernes/Sábado', 30.00, false, 20.00, ARRAY[5,6]::SMALLINT[], 10, true),
('12000000-0000-0000-0000-000000000003', '11000000-0000-0000-0000-000000000002', '[TEST] Precio Base Pepperoni',  42.00, true,  0.00, NULL, 0, true),
('12000000-0000-0000-0000-000000000004', '11000000-0000-0000-0000-000000000003', '[TEST] Precio Base Peroni',     14.00, true,  0.00, NULL, 0, true),
('22000000-0000-0000-0000-000000000001', '21000000-0000-0000-0000-000000000001', '[TEST] Precio Base Doble Smash',28.00, true,  0.00, NULL, 0, true);

-- ==============================================================================
-- 6. CARRITOS Y PRODUCTOS EN CARRITO
-- ==============================================================================
-- El total de carts se inicializa en 0.00 y se sincroniza automáticamente con el trigger de cart_items
INSERT INTO carts (id, tenant_id, customer_id, total, notes) VALUES
('30000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000004', 0.00, '[TEST] Carrito activo de Carlos en Bella Napoli');

-- Items en carrito (subtotal es GENERATED ALWAYS AS (quantity * unit_price), NO se inserta manualmente)
INSERT INTO cart_items (id, cart_id, tenant_id, product_id, quantity, unit_price, notes) VALUES
('31000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '11000000-0000-0000-0000-000000000001', 2, 38.00, '[TEST] Sin cebolla'),
('31000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '11000000-0000-0000-0000-000000000003', 3, 14.00, '[TEST] Heladas');
-- (Al insertar estos items, el trigger update_cart_total calculará automáticamente carts.total = 2*38 + 3*14 = 118.00)

-- ==============================================================================
-- 7. ÓRDENES, ÍTEMS Y PAGOS
-- ==============================================================================
-- Orden 1 en Bella Napoli (subtotal = 80.00, delivery = 5.00 -> total generado = 85.00)
INSERT INTO orders (id, tenant_id, customer_id, delivery_staff_id, address_id, order_number, delivery_type, status, delivery_address, delivery_location, subtotal, delivery_fee, notes) VALUES
('40000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000006', 'a0000000-0000-0000-0000-000000000001', 'ORD-001', 'DELIVERY', 'ENTREGADO', 'Av. Larco 456', ST_SetSRID(ST_MakePoint(-77.0298, -12.1221), 4326), 80.00, 5.00, '[TEST] Comanda inicial pagada');

-- Orden 1 en Burger House (mismo order_number 'ORD-001' pero en DISTINTO tenant: permitido por CONSTRAINT uk_orders_tenant_number)
INSERT INTO orders (id, tenant_id, customer_id, delivery_staff_id, address_id, order_number, delivery_type, status, delivery_address, delivery_location, subtotal, delivery_fee, notes) VALUES
('40000000-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000004', NULL, 'a0000000-0000-0000-0000-000000000001', 'ORD-001', 'TAKEAWAY', 'EN_PREPARACION', 'Para recoger en tienda', NULL, 28.00, 0.00, '[TEST] Pedido takeaway Burger');

-- Items de la orden (subtotal es GENERATED ALWAYS, NO se especifica en el INSERT)
INSERT INTO order_items (id, order_id, tenant_id, product_id, product_name, unit_price, quantity, notes) VALUES
('41000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '11000000-0000-0000-0000-000000000001', 'Pizza Margherita', 38.00, 1, '[TEST] Salsa extra'),
('41000000-0000-0000-0000-000000000002', '40000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '11000000-0000-0000-0000-000000000002', 'Pizza Pepperoni',  42.00, 1, '[TEST] Bien dorada');

INSERT INTO order_items (id, order_id, tenant_id, product_id, product_name, unit_price, quantity, notes) VALUES
('41000000-0000-0000-0000-000000000003', '40000000-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', '21000000-0000-0000-0000-000000000001', 'Doble Cheese Smash', 28.00, 1, '[TEST] Con pepinillos');

-- Pagos (con JSONB sanitizado sin datos de tarjeta ni PII sensible)
INSERT INTO payments (id, order_id, tenant_id, mp_payment_id, mp_preference_id, payment_method, status, amount, raw_response) VALUES
('42000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'MP-TEST-99887711', 'PREF-TEST-001', 'credit_card', 'APPROVED', 85.00,
 '{"id": 99887711, "status": "approved", "status_detail": "accredited", "payment_method_id": "visa", "transaction_amount": 85.00, "currency_id": "PEN"}'::JSONB);

-- ==============================================================================
-- 8. INVENTARIO: INSUMOS, STOCK Y MOVIMIENTOS
-- ==============================================================================
INSERT INTO inventory_items (id, tenant_id, name, sku, category, unit, cost_price, is_active) VALUES
('50000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '[TEST] Harina de Trigo 00', 'INS-HAR-01', 'Secos', 'KG', 4.50, true),
('50000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', '[TEST] Queso Mozzarella Bloque', 'INS-MOZ-01', 'Lácteos', 'KG', 26.00, true),
('50000000-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', '[TEST] Carne Molida Angus', 'INS-CAR-01', 'Cárnicos', 'KG', 32.00, true);

-- Stock (quantity >= 0 garantizado por CHECK)
INSERT INTO inventory_stocks (id, tenant_id, inventory_item_id, quantity, reserved_quantity, minimum_stock, maximum_stock, location, is_active) VALUES
('51000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '50000000-0000-0000-0000-000000000001', 50.000, 5.000, 10.000, 100.000, '[TEST] Almacén Secos - Estante A1', true),
('51000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', '50000000-0000-0000-0000-000000000002', 20.000, 2.000, 5.000,  40.000,  '[TEST] Cámara Fría 1', true),
('51000000-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', '50000000-0000-0000-0000-000000000003', 15.000, 0.000, 4.000,  30.000,  '[TEST] Congelador Carnes B', true);

-- Movimientos de inventario (cumpliendo CHECK de consistencia aritmética: new_quantity = previous_quantity + quantity)
INSERT INTO inventory_movements (id, tenant_id, inventory_stock_id, inventory_item_id, movement_type, reason, quantity, previous_quantity, new_quantity, performed_by_id, performed_by_name, reason_details) VALUES
('52000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '51000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000001', 'ENTRY', 'INITIAL_STOCK', 50.000, 0.000, 50.000, '00000000-0000-0000-0000-000000000002', 'Mario Rossi', '[TEST] Carga inicial de harina'),
('52000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', '51000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000002', 'ENTRY', 'PURCHASE',      20.000, 0.000, 20.000, '00000000-0000-0000-0000-000000000002', 'Mario Rossi', '[TEST] Factura proveedor F001-443');

COMMIT;

-- ==============================================================================
-- 9. SUITE DE CONSULTAS DE VERIFICACIÓN (EJECUTAR DESDE PGADMIN)
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- VERIFICACIÓN 1: Columnas Calculadas (3NF - GENERATED ALWAYS AS)
-- Comprueba que el subtotal y total se calcularon automáticamente sin manualidad.
-- ------------------------------------------------------------------------------
SELECT 
    oi.product_name,
    oi.quantity,
    oi.unit_price,
    oi.subtotal AS subtotal_generado_item,
    o.order_number,
    o.subtotal AS subtotal_orden,
    o.delivery_fee,
    o.total AS total_orden_generado
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
WHERE o.order_number = 'ORD-001' AND o.tenant_id = '11111111-1111-1111-1111-111111111111';

-- ------------------------------------------------------------------------------
-- VERIFICACIÓN 2: Triggers en Tiempo Real
-- Comprueba que carts.total fue sumado por el trigger (38*2 + 14*3 = 118.00)
-- y que tenant_customers acumuló total_orders = 1 por el trigger de órdenes.
-- ------------------------------------------------------------------------------
SELECT 
    c.id AS cart_id,
    c.total AS total_carrito_calculado_por_trigger,
    tc.total_orders AS ordenes_cliente_acumuladas_por_trigger,
    tc.first_order_at,
    tc.last_order_at
FROM carts c
JOIN tenant_customers tc ON tc.tenant_id = c.tenant_id AND tc.user_id = c.customer_id
WHERE c.tenant_id = '11111111-1111-1111-1111-111111111111';

-- ------------------------------------------------------------------------------
-- VERIFICACIÓN 3: Formato 1NF de días de la semana (Array SMALLINT[])
-- Consulta productos que apliquen el día Viernes (ISO 5) usando el operador de contención <@ o @>
-- ------------------------------------------------------------------------------
SELECT 
    p.name AS producto,
    pp.name AS promocion,
    pp.price AS precio_promo,
    pp.days_of_week AS dias_iso_permitidos
FROM product_prices pp
JOIN products p ON p.id = pp.product_id
WHERE pp.days_of_week @> ARRAY[5]::SMALLINT[];

-- ------------------------------------------------------------------------------
-- VERIFICACIÓN 4: Demostración de Row Level Security (RLS)
-- NOTA: Si ejecutas como superusuario ('postgres'), Postgres bypasea RLS por defecto.
-- Para simular el comportamiento real de la aplicación, ejecutamos dentro de un bloque
-- con el rol de la aplicación o verificamos asignando la variable de sesión.
-- ------------------------------------------------------------------------------

-- Paso 4.1: Consultar SIN contexto de tenant (no devuelve nada si RLS está activo para el usuario de app)
-- SET LOCAL app.current_tenant_id = '';
-- SELECT tenant_id, name FROM products;

-- Paso 4.2: Activar contexto de 'Pizzería Bella Napoli' (Tenant 1)
-- SET LOCAL app.current_tenant_id = '11111111-1111-1111-1111-111111111111';
-- SELECT id, tenant_id, name FROM products; 
-- (Solo devuelve las 3 pizzas de Bella Napoli; la hamburguesa de Burger House NO aparece)

-- Paso 4.3: Cambiar contexto a 'Burger House Gourmet' (Tenant 2)
-- SET LOCAL app.current_tenant_id = '22222222-2222-2222-2222-222222222222';
-- SELECT id, tenant_id, name FROM products;
-- (Solo devuelve la hamburguesa de Burger House)

-- ------------------------------------------------------------------------------
-- VERIFICACIÓN 5: Test de Restricciones y Protección Anti Cross-Tenant
-- (Estas consultas deben FALLAR intencionalmente si intentas violar las reglas)
-- ------------------------------------------------------------------------------

-- Test 5.1: Intentar meter producto de Tenant 2 en carrito de Tenant 1 (Falla por FK compuesta)
-- INSERT INTO cart_items (cart_id, tenant_id, product_id, quantity, unit_price)
-- VALUES ('30000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '21000000-0000-0000-0000-000000000001', 1, 28.00);
-- ERROR: insert or update on table "cart_items" violates foreign key constraint "cart_items_product_tenant_fkey"

-- Test 5.2: Intentar registrar stock negativo (Falla por CHECK)
-- UPDATE inventory_stocks SET quantity = -5.000 WHERE id = '51000000-0000-0000-0000-000000000001';
-- ERROR: new row for relation "inventory_stocks" violates check constraint "chk_inventory_stocks_quantity_non_negative"

-- Test 5.3: Intentar borrar usuario con órdenes (Falla por ON DELETE RESTRICT)
-- DELETE FROM users WHERE id = '00000000-0000-0000-0000-000000000004';
-- ERROR: update or delete on table "users" violates foreign key constraint "tenant_customers_user_id_fkey" on table "tenant_customers"
