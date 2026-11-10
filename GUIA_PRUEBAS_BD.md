# Guía de Pruebas y Verificación de Base de Datos en pgAdmin

Esta guía detalla los pasos para cargar datos de prueba desde [`script_inserciones.sql`](./script_inserciones.sql) y ejecutar consultas de verificación en **pgAdmin** para comprobar las mejoras de **Row Level Security (RLS)**, **columnas generadas (3NF)**, **triggers automáticos** y **restricciones de integridad**.

---

## 📋 Requisitos Previos

1. Tener una base de datos PostgreSQL (versión 13+) creada (ej. `nexofood_db`).
2. Haber ejecutado primero el esquema principal [`script.sql`](./script.sql) en dicha base de datos.
3. Contar con las extensiones `uuid-ossp`, `pgcrypto` y `postgis` habilitadas (el propio `script.sql` las crea).

---

## 🏢 Escenario de Prueba (Multi-Tenant)

El script de inserciones genera datos para dos restaurantes completamente independientes:

| Datos | Tenant 1 (Pizzería) | Tenant 2 (Hamburguesería) |
|---|---|---|
| **Nombre** | Pizzería Bella Napoli | Burger House Gourmet |
| **UUID Tenant** | `11111111-1111-1111-1111-111111111111` | `22222222-2222-2222-2222-222222222222` |
| **Slug** | `bella-napoli` | `burger-house` |
| **Dueño** | Mario Rossi (`mario@test.nexofood.lat`) | Bob Burger (`bob@test.nexofood.lat`) |
| **Productos** | Pizza Margherita, Pepperoni, Cerveza Peroni | Doble Cheese Smash |

---

## 🚀 Paso 1: Cargar los Datos de Prueba

1. Abre **pgAdmin** y conéctate al servidor PostgreSQL.
2. Despliega tu base de datos (ej. `nexofood_db`).
3. Abre la herramienta de consultas: haz clic derecho sobre la base de datos → **Query Tool** (o `Alt + Shift + Q`).
4. Abre el archivo [`script_inserciones.sql`](./script_inserciones.sql) con el ícono de carpeta **Open File**, o copia y pega su contenido en el editor.
5. Selecciona el bloque de inserción (desde `BEGIN;` hasta `COMMIT;`, líneas 1 a 130) y presiona **F5** (o el botón **Execute/Play ▶️**).
6. Verifica en la pestaña *Messages* el resultado:
   ```text
   Query returned successfully in ... ms.
   ```

---

## 🔍 Paso 2: Verificar Columnas Generadas (3NF - `GENERATED ALWAYS AS`)

Comprueba que `order_items.subtotal` y `orders.total` fueron calculados automáticamente por PostgreSQL a nivel de base de datos sin enviarlos en el `INSERT`:

```sql
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
WHERE o.order_number = 'ORD-001' 
  AND o.tenant_id = '11111111-1111-1111-1111-111111111111';
```

**Resultado esperado:**
- `subtotal_generado_item` mostrará `38.00` y `42.00` (`quantity * unit_price`).
- `total_orden_generado` mostrará `85.00` (`subtotal (80.00) + delivery_fee (5.00)`).

---

## ⚡ Paso 3: Verificar Triggers Automáticos en Tiempo Real

Comprueba que:
1. `carts.total` fue calculado y sumado por el trigger `trg_cart_items_sync_total` (2 pizzas de 38 + 3 bebidas de 14 = 118.00).
2. `tenant_customers.total_orders` fue incrementado a `1` por el trigger `trg_orders_sync_customer_stats`:

```sql
SELECT 
    c.id AS cart_id,
    c.total AS total_carrito_calculado_por_trigger,
    tc.total_orders AS ordenes_cliente_acumuladas_por_trigger,
    tc.first_order_at,
    tc.last_order_at
FROM carts c
JOIN tenant_customers tc ON tc.id = c.customer_id
WHERE c.tenant_id = '11111111-1111-1111-1111-111111111111';
```

**Resultado esperado:**
- `total_carrito_calculado_por_trigger` = `118.00`.
- `ordenes_cliente_acumuladas_por_trigger` = `1`.

---

## 📅 Paso 4: Verificar Consultas 1NF con Arreglos Tipados (`SMALLINT[]`)

Consulta qué promociones aplican los días **Viernes** (código ISO 5) aprovechando el índice `GIN` y el operador de contención `@>`:

```sql
SELECT 
    p.name AS producto,
    pp.name AS promocion,
    pp.price AS precio_promocional,
    pp.days_of_week AS dias_iso_permitidos
FROM product_prices pp
JOIN products p ON p.id = pp.product_id
WHERE pp.days_of_week @> ARRAY[5]::SMALLINT[];
```

**Resultado esperado:**
- Muestra la promoción `Happy Hour Viernes/Sábado` (con `days_of_week = {5,6}`) para la Pizza Margherita.

---

## 🛡️ Paso 5: Demostración de Row Level Security (RLS)

> [!IMPORTANT]
> **Comportamiento de superusuario en Postgres:**
> Si en pgAdmin estás conectado con el usuario superadministrador `postgres`, PostgreSQL **ignora RLS por defecto**.
> Para probar RLS en pgAdmin como lo hace la API Spring Boot, puedes usar cualquiera de las siguientes 2 opciones:

### Opción A (Recomendada): Usar un rol de aplicación no-superuser

1. Ejecuta una sola vez para crear el rol de prueba:
   ```sql
   CREATE ROLE nexofood_app WITH LOGIN PASSWORD 'app_password';
   GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO nexofood_app;
   GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO nexofood_app;
   ```

2. **Prueba Tenant 1 (Pizzería Bella Napoli):**
   ```sql
   BEGIN;
   SET LOCAL ROLE nexofood_app;
   SET LOCAL app.current_tenant_id = '11111111-1111-1111-1111-111111111111';

   -- Consulta de productos sin WHERE
   SELECT id, tenant_id, name FROM products;
   COMMIT;
   ```
   *Resultado:* Solo devuelve los 3 productos de Bella Napoli (*Pizza Margherita*, *Pizza Pepperoni*, *Cerveza Peroni*). La hamburguesa de Burger House **no aparece**.

3. **Prueba Tenant 2 (Burger House Gourmet):**
   ```sql
   BEGIN;
   SET LOCAL ROLE nexofood_app;
   SET LOCAL app.current_tenant_id = '22222222-2222-2222-2222-222222222222';

   SELECT id, tenant_id, name FROM products;
   COMMIT;
   ```
   *Resultado:* Solo devuelve *Doble Cheese Smash*.

### Opción B: Forzar RLS en la tabla para el usuario actual
Si prefieres no crear otro rol, puedes forzar que RLS aplique incluso al superusuario:

```sql
ALTER TABLE products FORCE ROW LEVEL SECURITY;

-- Establecer Tenant 1:
SET app.current_tenant_id = '11111111-1111-1111-1111-111111111111';
SELECT tenant_id, name FROM products;

-- Establecer Tenant 2:
SET app.current_tenant_id = '22222222-2222-2222-2222-222222222222';
SELECT tenant_id, name FROM products;

-- (Opcional) Revertir al terminar:
-- ALTER TABLE products NO FORCE ROW LEVEL SECURITY;
```

---

## 🚫 Paso 6: Pruebas de Restricciones Negativas (Deben Fallar)

Estas consultas prueban que la base de datos rechaza operaciones inválidas automáticamente:

### Test 6.1: Intento de inyección Cross-Tenant en Carrito
Intentar asociar la hamburguesa de Burger House (`21000000-0000-0000-0000-000000000001`) al carrito de Bella Napoli (`30000000-0000-0000-0000-000000000001`):

```sql
INSERT INTO cart_items (cart_id, tenant_id, product_id, quantity, unit_price)
VALUES (
    '30000000-0000-0000-0000-000000000001', 
    '11111111-1111-1111-1111-111111111111', 
    '21000000-0000-0000-0000-000000000001', 
    1, 
    28.00
);
```
**Error obtenido de PostgreSQL:**
```text
ERROR: insert or update on table "cart_items" violates foreign key constraint "cart_items_product_tenant_fkey"
DETAIL: Key (product_id, tenant_id)=(21000000-0000-0000-0000-000000000001, 11111111-1111-1111-1111-111111111111) is not present in table "products".
```

---

### Test 6.2: Intento de registrar inventario negativo
Intentar descontar stock a números negativos:

```sql
UPDATE inventory_stocks 
SET quantity = -5.000 
WHERE id = '51000000-0000-0000-0000-000000000001';
```
**Error obtenido de PostgreSQL:**
```text
ERROR: new row for relation "inventory_stocks" violates check constraint "chk_inventory_stocks_quantity_non_negative"
```

---

### Test 6.3: Intento de eliminación física destructiva (`ON DELETE RESTRICT`)
Intentar borrar al cliente Carlos que ya tiene órdenes emitidas:

```sql
DELETE FROM users WHERE id = '00000000-0000-0000-0000-000000000004';
```
**Error obtenido de PostgreSQL:**
```text
ERROR: update or delete on table "users" violates foreign key constraint "tenant_customers_user_id_fkey" on table "tenant_customers"
```
*(Para dar de baja a un usuario se debe usar el soft delete: `UPDATE users SET deleted_at = NOW() WHERE id = ...;`).*
