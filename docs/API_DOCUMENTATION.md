# 📖 NexoFood API — Documentación para Frontend

> **Proyecto:** NexoFood API — Plataforma SaaS Multi-Tenant para Restaurantes  
> **Stack:** Java 21 · Spring Boot 4.1.1 · PostgreSQL 16 + PostGIS · Hibernate Spatial  
> **Versión:** 1.0  
> **Última actualización:** 2026-09-26  
> **Guía de estilo:** [DOCUMENTATION_GUIDE.md](./DOCUMENTATION_GUIDE.md)

---

## 📌 Información General

| Campo                  | Valor                                            |
|------------------------|--------------------------------------------------|
| **Base URL (Local)**   | `http://localhost:8080/api/v1`                   |
| **Base URL (Prod)**    | `https://api.nexofood.lat/api/v1`                |
| **Formato**            | `application/json`                               |
| **Autenticación**      | JWT Bearer Token en header `Authorization`       |
| **Versión**            | `v1`                                             |
| **Swagger UI**         | `http://localhost:8080/swagger-ui/index.html`    |
| **OpenAPI JSON**       | `http://localhost:8080/v3/api-docs`              |

---

## 🏪 Arquitectura Multi-Tenant

NexoFood es una plataforma **SaaS multi-tenant** donde cada restaurante es un **Tenant** independiente:

```
Usuario (User)
  └── Suscripción (Subscription) ← vinculada a un Plan
        └── Tenant (Restaurante) ← 1:1 con suscripción y owner
              ├── Miembros (TenantMembers) con roles: OWNER, ADMIN, COCINERO, REPARTIDOR
              ├── Catálogo: Categorías → Productos
              ├── Carritos (1 por cliente por restaurante)
              ├── Pedidos → Items → Pagos (Mercado Pago)
              └── Inventario → Items → Movimientos de Stock
```

**Reglas clave para el frontend:**
- Un usuario solo puede ser **owner de un restaurante**.
- Los carritos son **únicos por combinación (tenant, customer)**.
- Los pedidos validan **cobertura geoespacial** con PostGIS (la dirección debe estar dentro del radio de delivery del restaurante).
- Los pedidos no pasan a `EN_PREPARACION` sin pago aprobado (anti-fraude).
- Todos los IDs son **UUID** (strings de 36 caracteres).

---

## 🔑 Autenticación

- **Tipo:** JWT (JSON Web Token) con firma HMAC-SHA256
- **Envío:** Header `Authorization: Bearer <accessToken>`
- **Access Token:** Expira en **15 minutos** (900 segundos)
- **Refresh Token:** Expira en **7 días**, con **rotación automática** en cada refresh
- **Renovación:** `POST /api/v1/auth/refresh` enviando `{ "refreshToken": "..." }`
- **Expiración:** El servidor responde con `401 Unauthorized`
- **Seguridad avanzada:** Si se detecta reutilización de un refresh token ya revocado, se revocan **TODOS** los tokens del usuario inmediatamente

### Claims del Access Token

| Claim    | Tipo   | Descripción                                |
|----------|--------|--------------------------------------------|
| `sub`    | string | Email del usuario                          |
| `role`   | string | Rol del sistema (`SUPERADMIN` o `USER`)    |
| `userId` | string | UUID del usuario                           |
| `iat`    | number | Timestamp de emisión                       |
| `exp`    | number | Timestamp de expiración                    |

### Flujo de autenticación

```
1. POST /api/v1/auth/register o /api/v1/auth/login
2. El servidor retorna accessToken + refreshToken en el body
3. El frontend almacena ambos tokens (localStorage, estado, etc.)
4. Cada request autenticada incluye header: Authorization: Bearer <accessToken>
5. Si el accessToken expira (401) → POST /api/v1/auth/refresh con el refreshToken
6. El servidor retorna nuevos accessToken + refreshToken (rotación)
7. Si el refresh también falla → redirigir al login
```

### Configuración recomendada con Axios

```javascript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
  headers: {
    'Content-Type': 'application/json'
  }
});

// Interceptor para agregar token a cada request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Interceptor para renovar token automáticamente
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        const refreshToken = localStorage.getItem('refreshToken');
        const { data } = await axios.post(
          'http://localhost:8080/api/v1/auth/refresh',
          { refreshToken }
        );

        // Guardar nuevos tokens (rotación)
        localStorage.setItem('accessToken', data.data.accessToken);
        localStorage.setItem('refreshToken', data.data.refreshToken);

        // Reintentar la petición original con el nuevo token
        originalRequest.headers.Authorization = `Bearer ${data.data.accessToken}`;
        return api.request(originalRequest);
      } catch {
        // Refresh falló → limpiar y redirigir al login
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default api;
```

---

## 📤 Envelopes de Respuesta

### ✅ Respuesta exitosa: `ApiResponse<T>`

Todas las respuestas exitosas siguen este formato:

```json
{
  "success": true,
  "message": "Mensaje descriptivo del resultado",
  "data": { ... }
}
```

| Campo     | Tipo    | Descripción                                      |
|-----------|---------|--------------------------------------------------|
| `success` | boolean | Siempre `true` en respuestas exitosas            |
| `message` | string  | Mensaje legible (ej: "Usuario registrado exitosamente") |
| `data`    | T       | Payload del recurso (varía según el endpoint)    |

### ❌ Respuesta de error: `ErrorResponse`

Todas las respuestas de error siguen este formato:

```json
{
  "message": "Descripción del error",
  "error": "TIPO_DE_ERROR",
  "status": 400,
  "timestamp": "2026-09-26T14:00:00",
  "path": "/api/v1/auth/register",
  "method": "POST"
}
```

| Campo       | Tipo   | Descripción                                           |
|-------------|--------|-------------------------------------------------------|
| `message`   | string | Descripción legible del error                         |
| `error`     | string | Tipo de error HTTP (ej: `BAD_REQUEST`, `UNAUTHORIZED`)|
| `status`    | number | Código de estado HTTP                                 |
| `timestamp` | string | Fecha y hora del error                                |
| `path`      | string | Ruta del endpoint que generó el error                 |
| `method`    | string | Método HTTP de la petición (GET, POST, etc.)          |

### Códigos HTTP

| Código | Error                  | Cuándo se retorna                                              |
|--------|------------------------|----------------------------------------------------------------|
| `200`  | OK                     | Operación exitosa (lectura, actualización, login, refresh)     |
| `201`  | Created                | Recurso creado exitosamente (registro)                         |
| `400`  | Bad Request            | Error de validación de campos                                  |
| `401`  | Unauthorized           | Token ausente, inválido, expirado o credenciales incorrectas   |
| `403`  | Forbidden              | Sin permisos o cuenta desactivada                              |
| `404`  | Not Found              | Recurso no encontrado                                          |
| `409`  | Conflict               | Recurso duplicado (ej: email ya registrado)                    |
| `500`  | Internal Server Error  | Error interno del servidor                                     |

---

## 📦 Modelos de Datos (Entidades)

### 📦 Modelo: User

| Campo        | Tipo          | Nullable | Descripción                                  |
|--------------|---------------|----------|----------------------------------------------|
| `id`         | UUID (string) | No       | Identificador único                          |
| `email`      | string        | No       | Correo electrónico (único)                   |
| `fullName`   | string        | No       | Nombre completo (max 150)                    |
| `phone`      | string        | Sí       | Número de teléfono (max 20)                  |
| `systemRole` | string        | No       | Rol del sistema: `"SUPERADMIN"` o `"USER"`   |
| `isActive`   | boolean       | No       | Si el usuario está activo                    |
| `createdAt`  | string        | No       | Fecha de creación (ISO 8601)                 |
| `updatedAt`  | string        | No       | Fecha de actualización (ISO 8601)            |

> **Nota:** El campo `passwordHash` nunca se retorna en las respuestas.

---

### 📦 Modelo: CustomerAddress

| Campo         | Tipo          | Nullable | Descripción                                    |
|---------------|---------------|----------|------------------------------------------------|
| `id`          | UUID (string) | No       | Identificador único                            |
| `userId`      | UUID (string) | No       | ID del usuario propietario                     |
| `title`       | string        | Sí       | Título de la dirección (default: "Casa")       |
| `addressLine` | string        | No       | Dirección completa en texto                    |
| `reference`   | string        | Sí       | Referencia adicional                           |
| `latitude`    | number        | No       | Latitud (WGS84 SRID 4326)                     |
| `longitude`   | number        | No       | Longitud (WGS84 SRID 4326)                    |

---

### 📦 Modelo: SubscriptionPlan

| Campo              | Tipo          | Nullable | Descripción                              |
|--------------------|---------------|----------|------------------------------------------|
| `id`               | UUID (string) | No       | Identificador único                      |
| `name`             | string        | No       | Nombre del plan (max 100)                |
| `price`            | number        | No       | Precio del plan (BigDecimal)             |
| `billingCycleDays` | number        | No       | Días del ciclo de facturación (default 30)|
| `maxProducts`      | number        | Sí       | Límite de productos permitidos           |
| `isActive`         | boolean       | No       | Si el plan está activo                   |

---

### 📦 Modelo: Subscription

| Campo             | Tipo          | Nullable | Descripción                                     |
|-------------------|---------------|----------|-------------------------------------------------|
| `id`              | UUID (string) | No       | Identificador único                             |
| `planId`          | UUID (string) | No       | ID del plan de suscripción                      |
| `userId`          | UUID (string) | No       | ID del usuario suscrito                         |
| `status`          | string        | No       | Estado (ver enum abajo)                         |
| `startDate`       | string        | No       | Fecha de inicio (ISO 8601)                      |
| `endDate`         | string        | No       | Fecha de fin (ISO 8601)                         |
| `mpPreapprovalId` | string        | Sí       | ID de preaprobación de Mercado Pago             |

**Enum `SubscriptionStatus`:** `TRIAL`, `ACTIVE`, `PAST_DUE`, `CANCELED`, `EXPIRED`

---

### 📦 Modelo: Tenant (Restaurante)

| Campo               | Tipo          | Nullable | Descripción                                      |
|---------------------|---------------|----------|--------------------------------------------------|
| `id`                | UUID (string) | No       | Identificador único                              |
| `subscriptionId`    | UUID (string) | No       | ID de la suscripción (relación 1:1)              |
| `ownerId`           | UUID (string) | No       | ID del usuario propietario (relación 1:1)        |
| `name`              | string        | No       | Nombre del restaurante (max 150)                 |
| `slug`              | string        | No       | Slug URL-friendly (único, regex: `^[a-z0-9-]+$`) |
| `logoUrl`           | string        | Sí       | URL del logo                                     |
| `bannerUrl`         | string        | Sí       | URL del banner                                   |
| `phone`             | string        | Sí       | Teléfono del restaurante                         |
| `address`           | string        | Sí       | Dirección del restaurante                        |
| `latitude`          | number        | Sí       | Latitud (WGS84 SRID 4326)                       |
| `longitude`         | number        | Sí       | Longitud (WGS84 SRID 4326)                      |
| `deliveryRadiusKm`  | number        | No       | Radio de delivery en km (default 5.00)           |
| `defaultDeliveryFee`| number        | No       | Costo de delivery por defecto (default 0.00)     |
| `isActive`          | boolean       | No       | Si el restaurante está activo                    |

---

### 📦 Modelo: TenantMember

| Campo      | Tipo          | Nullable | Descripción                                                    |
|------------|---------------|----------|----------------------------------------------------------------|
| `id`       | UUID (string) | No       | Identificador único                                            |
| `tenantId` | UUID (string) | No       | ID del restaurante                                             |
| `userId`   | UUID (string) | No       | ID del usuario miembro                                         |
| `role`     | string        | No       | Rol en el staff (ver enum abajo)                               |
| `isActive` | boolean       | No       | Si el miembro está activo                                      |

**Enum `TenantStaffRole`:** `OWNER`, `ADMIN`, `COCINERO`, `REPARTIDOR`

---

### 📦 Modelo: Category

| Campo         | Tipo          | Nullable | Descripción                                |
|---------------|---------------|----------|--------------------------------------------|
| `id`          | UUID (string) | No       | Identificador único                        |
| `tenantId`    | UUID (string) | No       | ID del restaurante al que pertenece        |
| `name`        | string        | No       | Nombre de la categoría (max 100)           |
| `description` | string        | Sí       | Descripción de la categoría                |
| `sortOrder`   | number        | No       | Orden de visualización (default 0)         |
| `isActive`    | boolean       | No       | Si la categoría está activa                |

---

### 📦 Modelo: Product

| Campo         | Tipo          | Nullable | Descripción                                 |
|---------------|---------------|----------|---------------------------------------------|
| `id`          | UUID (string) | No       | Identificador único                         |
| `tenantId`    | UUID (string) | No       | ID del restaurante al que pertenece         |
| `categoryId`  | UUID (string) | Sí       | ID de la categoría (ON DELETE SET NULL)      |
| `name`        | string        | No       | Nombre del producto (max 150)               |
| `description` | string        | Sí       | Descripción del producto                    |
| `price`       | number        | No       | Precio del producto (BigDecimal ≥ 0.01)     |
| `imageUrl`    | string        | Sí       | URL de la imagen del producto               |
| `isAvailable` | boolean       | No       | Si el producto está disponible              |

---

### 📦 Modelo: Cart

| Campo        | Tipo          | Nullable | Descripción                                          |
|--------------|---------------|----------|------------------------------------------------------|
| `id`         | UUID (string) | No       | Identificador único                                  |
| `tenantId`   | UUID (string) | No       | ID del restaurante                                   |
| `customerId` | UUID (string) | No       | ID del cliente                                       |
| `total`      | number        | No       | Total del carrito (default 0.00)                     |
| `notes`      | string        | Sí       | Notas del carrito                                    |
| `items`      | CartItem[]    | No       | Lista de items en el carrito                         |

> **Unique Constraint:** `(tenantId, customerId)` — un cliente solo tiene 1 carrito por restaurante.

---

### 📦 Modelo: CartItem

| Campo       | Tipo          | Nullable | Descripción                              |
|-------------|---------------|----------|------------------------------------------|
| `id`        | UUID (string) | No       | Identificador único                      |
| `cartId`    | UUID (string) | No       | ID del carrito                           |
| `productId` | UUID (string) | No       | ID del producto                          |
| `quantity`  | number        | No       | Cantidad (default 1)                     |
| `unitPrice` | number        | No       | Precio unitario al momento de agregar    |
| `subtotal`  | number        | No       | Subtotal (quantity × unitPrice)          |
| `notes`     | string        | Sí       | Notas del item                           |

---

### 📦 Modelo: Order

| Campo              | Tipo          | Nullable | Descripción                                        |
|--------------------|---------------|----------|----------------------------------------------------|
| `id`               | UUID (string) | No       | Identificador único                                |
| `tenantId`         | UUID (string) | No       | ID del restaurante                                 |
| `customerId`       | UUID (string) | No       | ID del cliente                                     |
| `deliveryStaffId`  | UUID (string) | Sí       | ID del repartidor asignado                         |
| `orderNumber`      | string        | No       | Número de pedido legible (max 20)                  |
| `deliveryType`     | string        | No       | Tipo de entrega (ver enum)                         |
| `status`           | string        | No       | Estado del pedido (ver enum)                       |
| `deliveryAddress`  | string        | Sí       | Snapshot histórico inmutable de la dirección        |
| `deliveryLatitude` | number        | Sí       | Latitud de entrega                                 |
| `deliveryLongitude`| number        | Sí       | Longitud de entrega                                |
| `subtotal`         | number        | No       | Subtotal de los items                              |
| `deliveryFee`      | number        | No       | Costo de delivery (default 0.00)                   |
| `total`            | number        | No       | Total del pedido                                   |
| `notes`            | string        | Sí       | Notas del pedido                                   |
| `items`            | OrderItem[]   | No       | Items del pedido                                   |
| `createdAt`        | string        | No       | Fecha de creación (ISO 8601)                       |
| `updatedAt`        | string        | No       | Fecha de actualización (ISO 8601)                  |

**Enums:**

| Enum            | Valores                                                                            |
|-----------------|-------------------------------------------------------------------------------------|
| `DeliveryType`  | `DELIVERY`, `TAKEAWAY`, `DINE_IN`                                                  |
| `OrderStatus`   | `PENDIENTE`, `EN_PREPARACION`, `LISTO_PARA_ENTREGA`, `EN_CAMINO`, `ENTREGADO`, `CANCELADO` |

### Flujo de estados del pedido

```
PENDIENTE → EN_PREPARACION → LISTO_PARA_ENTREGA → EN_CAMINO → ENTREGADO
    ↓            ↓                  ↓                 ↓
                          CANCELADO (desde cualquier estado previo a ENTREGADO)
```

> **⚠️ Regla de negocio:** Un pedido no puede pasar a `EN_PREPARACION` sin que el pago esté `APPROVED`.

---

### 📦 Modelo: OrderItem

| Campo         | Tipo          | Nullable | Descripción                                          |
|---------------|---------------|----------|------------------------------------------------------|
| `id`          | UUID (string) | No       | Identificador único                                  |
| `orderId`     | UUID (string) | No       | ID del pedido                                        |
| `productId`   | UUID (string) | Sí       | ID del producto (SET NULL si se elimina)             |
| `productName` | string        | No       | Nombre del producto (**snapshot histórico inmutable**)|
| `unitPrice`   | number        | No       | Precio unitario (**snapshot histórico inmutable**)   |
| `quantity`    | number        | No       | Cantidad                                             |
| `subtotal`    | number        | No       | Subtotal del item                                    |
| `notes`       | string        | Sí       | Notas del item                                       |

> **📝 Snapshots históricos:** `productName` y `unitPrice` se guardan al momento del pedido y nunca cambian, incluso si el producto se actualiza o elimina después.

---

### 📦 Modelo: Payment

| Campo            | Tipo          | Nullable | Descripción                                     |
|------------------|---------------|----------|-------------------------------------------------|
| `id`             | UUID (string) | No       | Identificador único                             |
| `orderId`        | UUID (string) | No       | ID del pedido (relación 1:1)                    |
| `tenantId`       | UUID (string) | No       | ID del restaurante                              |
| `mpPaymentId`    | string        | Sí       | ID de transacción en Mercado Pago               |
| `mpPreferenceId` | string        | Sí       | ID de preferencia de Checkout Pro               |
| `paymentMethod`  | string        | Sí       | Método de pago                                  |
| `status`         | string        | No       | Estado del pago (ver enum)                      |
| `amount`         | number        | No       | Monto del pago (BigDecimal ≥ 0.01)             |
| `rawResponse`    | object        | Sí       | Respuesta completa de MP (JSONB, para auditoría)|

**Enum `PaymentStatus`:** `PENDING`, `APPROVED`, `REJECTED`, `REFUNDED`

---

### 📦 Modelo: InventoryItem

| Campo         | Tipo          | Nullable | Descripción                              |
|---------------|---------------|----------|------------------------------------------|
| `id`          | UUID (string) | No       | Identificador único                      |
| `tenantId`    | UUID (string) | No       | ID del restaurante                       |
| `name`        | string        | No       | Nombre del item de inventario (max 150)  |
| `sku`         | string        | Sí       | Código SKU (max 50)                      |
| `description` | string        | Sí       | Descripción                              |
| `category`    | string        | Sí       | Categoría del item (max 100)             |
| `unit`        | string        | No       | Unidad de medida: `UNIT` o `KG`         |
| `costPrice`   | number        | No       | Costo del item (default 0.00)           |
| `isActive`    | boolean       | No       | Si el item está activo                   |

---

### 📦 Modelo: Inventory (Stock)

| Campo             | Tipo          | Nullable | Descripción                                   |
|-------------------|---------------|----------|-----------------------------------------------|
| `id`              | UUID (string) | No       | Identificador único                           |
| `tenantId`        | UUID (string) | No       | ID del restaurante                            |
| `itemId`          | UUID (string) | No       | ID del item de inventario                     |
| `unit`            | string        | No       | Unidad de medida: `UNIT` o `KG`              |
| `quantity`        | number        | No       | Cantidad actual en stock                      |
| `reservedQuantity`| number        | No       | Cantidad reservada (pedidos en proceso)       |
| `minimumStock`    | number        | No       | Stock mínimo (alerta)                         |
| `maximumStock`    | number        | Sí       | Stock máximo permitido                        |
| `location`        | string        | Sí       | Ubicación física en almacén (max 150)         |
| `isActive`        | boolean       | No       | Si el registro está activo                    |

---

### 📦 Modelo: InventoryMovement

| Campo             | Tipo          | Nullable | Descripción                                  |
|-------------------|---------------|----------|----------------------------------------------|
| `id`              | UUID (string) | No       | Identificador único                          |
| `tenantId`        | UUID (string) | No       | ID del restaurante                           |
| `inventoryId`     | UUID (string) | No       | ID del inventario                            |
| `itemId`          | UUID (string) | No       | ID del item de inventario                    |
| `movementType`    | string        | No       | Tipo: `ENTRY`, `EXIT`, `ADJUSTMENT`         |
| `reason`          | string        | No       | Razón del movimiento (ver enums abajo)       |
| `quantity`        | number        | No       | Cantidad del movimiento                      |
| `previousQuantity`| number        | No       | Cantidad antes del movimiento                |
| `newQuantity`     | number        | No       | Cantidad después del movimiento              |
| `performedBy`     | UUID (string) | No       | ID del usuario que realizó el movimiento     |
| `performedByName` | string        | Sí       | Nombre del usuario                           |
| `reasonDetails`   | string        | No       | Detalle/justificación del movimiento         |

**Enums de Inventory:**

| Enum                      | Valores                                                                                              |
|---------------------------|------------------------------------------------------------------------------------------------------|
| `InventoryMovementType`   | `ENTRY`, `EXIT`, `ADJUSTMENT`                                                                       |
| `InventoryMovementReason` | Entradas: `PURCHASE`, `INITIAL_STOCK`, `CUSTOMER_RETURN`, `TRANSFER_IN`                             |
|                           | Salidas: `KITCHEN_CONSUMPTION_OR_WASTE`, `EXPIRED_OR_SPOILAGE`, `DAMAGED_OR_LOSS`, `SALE`, `INTERNAL_CONSUMPTION`, `SUPPLIER_RETURN`, `TRANSFER_OUT` |
|                           | Ajustes: `PHYSICAL_COUNT`, `CORRECTION`, `OTHER`                                                    |

---

---

# 📡 Endpoints

---

## 🟢 Estado de Implementación

| Módulo         | Estado                        | Endpoints                    |
|----------------|-------------------------------|------------------------------|
| **Identity**   | ✅ Implementado y testeado    | register, login, refresh     |
| **Subscription** | 🔧 DTOs y repos listos     | Pendiente de controllers     |
| **Tenant**     | 🔧 DTOs y repos listos       | Pendiente de controllers     |
| **Catalog**    | 🔧 DTOs y repos listos       | Pendiente de controllers     |
| **Cart**       | 🔧 DTOs y repos listos       | Pendiente de controllers     |
| **Order**      | 🔧 DTOs y repos listos       | Pendiente de controllers     |
| **Payment**    | 🔧 DTOs y repos listos       | Pendiente de controllers     |
| **Inventory**  | 🔧 DTOs y repos listos       | Pendiente de controllers     |

---

## 1. Identity — Autenticación

---

### POST `/api/v1/auth/register`

> Registra un nuevo usuario en el sistema y retorna tokens de acceso.

**Autenticación:** No requerida  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

#### 📥 Request

**Body (JSON):**

| Campo      | Tipo   | Requerido | Validación                   | Descripción            |
|------------|--------|-----------|------------------------------|------------------------|
| `email`    | string | Sí        | Email válido, max 255 chars  | Correo electrónico     |
| `password` | string | Sí        | min 8, max 100 caracteres    | Contraseña             |
| `fullName` | string | Sí        | max 150 caracteres           | Nombre completo        |
| `phone`    | string | No        | max 20 caracteres            | Número de teléfono     |

**Ejemplo de request:**

```json
{
  "email": "carlos@ejemplo.com",
  "password": "Password123!",
  "fullName": "Carlos Rodríguez",
  "phone": "+51999888777"
}
```

#### 📤 Response

**Éxito (201 Created):**

```json
{
  "success": true,
  "message": "Usuario registrado exitosamente",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "email": "carlos@ejemplo.com",
      "fullName": "Carlos Rodríguez",
      "phone": "+51999888777",
      "systemRole": "USER",
      "isActive": true,
      "createdAt": "2026-09-26T14:00:00Z",
      "updatedAt": "2026-09-26T14:00:00Z"
    }
  }
}
```

**Error (400 Bad Request) — Validación de campos:**

```json
{
  "message": "Error de validación: email: Formato de email inválido",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-09-26T14:00:00",
  "path": "/api/v1/auth/register",
  "method": "POST"
}
```

**Error (409 Conflict) — Email duplicado:**

```json
{
  "message": "El correo electrónico ya está registrado",
  "error": "CONFLICT",
  "status": 409,
  "timestamp": "2026-09-26T14:00:00",
  "path": "/api/v1/auth/register",
  "method": "POST"
}
```

---

### POST `/api/v1/auth/login`

> Inicia sesión con credenciales existentes. Revoca tokens previos y emite nuevos.

**Autenticación:** No requerida  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

#### 📥 Request

**Body (JSON):**

| Campo      | Tipo   | Requerido | Validación                   | Descripción          |
|------------|--------|-----------|------------------------------|----------------------|
| `email`    | string | Sí        | Email válido, max 255 chars  | Correo electrónico   |
| `password` | string | Sí        | Obligatorio                  | Contraseña           |

**Ejemplo de request:**

```json
{
  "email": "carlos@ejemplo.com",
  "password": "Password123!"
}
```

#### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Inicio de sesión exitoso",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "email": "carlos@ejemplo.com",
      "fullName": "Carlos Rodríguez",
      "phone": "+51999888777",
      "systemRole": "USER",
      "isActive": true,
      "createdAt": "2026-09-26T14:00:00Z",
      "updatedAt": "2026-09-26T14:00:00Z"
    }
  }
}
```

**Error (400 Bad Request) — Validación:**

```json
{
  "message": "Error de validación: password: La contraseña es obligatoria",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-09-26T14:00:00",
  "path": "/api/v1/auth/login",
  "method": "POST"
}
```

**Error (401 Unauthorized) — Credenciales incorrectas:**

```json
{
  "message": "Las credenciales proporcionadas son incorrectas",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-09-26T14:00:00",
  "path": "/api/v1/auth/login",
  "method": "POST"
}
```

**Error (401 Unauthorized) — Cuenta desactivada:**

```json
{
  "message": "La cuenta de usuario está desactivada",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-09-26T14:00:00",
  "path": "/api/v1/auth/login",
  "method": "POST"
}
```

#### 💻 Ejemplo de integración (fetch)

```javascript
const response = await fetch('http://localhost:8080/api/v1/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    email: 'carlos@ejemplo.com',
    password: 'Password123!'
  })
});

const data = await response.json();

if (data.success) {
  // Guardar tokens
  localStorage.setItem('accessToken', data.data.accessToken);
  localStorage.setItem('refreshToken', data.data.refreshToken);

  // Guardar información del usuario
  const user = data.data.user;
  console.log(`Bienvenido ${user.fullName} (${user.systemRole})`);
} else {
  console.error(data.message);
}
```

---

### POST `/api/v1/auth/refresh`

> Renueva los tokens usando el refresh token. Implementa **rotación**: el refresh token anterior se revoca y se emite uno nuevo.

**Autenticación:** No requerida (recibe el refresh token en el body)  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

#### 📥 Request

**Body (JSON):**

| Campo          | Tipo   | Requerido | Validación     | Descripción                   |
|----------------|--------|-----------|----------------|-------------------------------|
| `refreshToken` | string | Sí        | No en blanco   | Refresh token recibido en login/register |

**Ejemplo de request:**

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

#### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Token renovado exitosamente",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... (nuevo)",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... (nuevo, rotado)",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "email": "carlos@ejemplo.com",
      "fullName": "Carlos Rodríguez",
      "phone": "+51999888777",
      "systemRole": "USER",
      "isActive": true,
      "createdAt": "2026-09-26T14:00:00Z",
      "updatedAt": "2026-09-26T14:00:00Z"
    }
  }
}
```

> **⚠️ Importante:** Al recibir esta respuesta, el frontend **debe reemplazar AMBOS tokens** (access y refresh) con los nuevos valores. El refresh token anterior ya fue revocado.

**Error (401 Unauthorized) — Token inválido o expirado:**

```json
{
  "message": "El token de refresco es inválido o ha expirado",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-09-26T14:00:00",
  "path": "/api/v1/auth/refresh",
  "method": "POST"
}
```

**Error (401 Unauthorized) — Token revocado (detección de reutilización):**

```json
{
  "message": "El token de refresco ha sido revocado",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-09-26T14:00:00",
  "path": "/api/v1/auth/refresh",
  "method": "POST"
}
```

> **🔒 Seguridad:** Si se detecta un intento de reutilizar un refresh token ya revocado, el servidor **revoca TODOS los tokens del usuario** como medida de protección. El frontend debe redirigir al login.

#### 💻 Ejemplo de integración (fetch)

```javascript
const refreshToken = localStorage.getItem('refreshToken');

const response = await fetch('http://localhost:8080/api/v1/auth/refresh', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ refreshToken })
});

const data = await response.json();

if (data.success) {
  // IMPORTANTE: Reemplazar AMBOS tokens
  localStorage.setItem('accessToken', data.data.accessToken);
  localStorage.setItem('refreshToken', data.data.refreshToken);
} else {
  // Token de refresco inválido → redirigir al login
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  window.location.href = '/login';
}
```

> **📝 Notas para el Frontend:**
> - Los tokens se envían en el **header `Authorization: Bearer <token>`**, NO en cookies.
> - El `expiresIn` indica los segundos de validez del access token (900 = 15 minutos).
> - Siempre reemplazar ambos tokens tras un refresh exitoso (rotación).
> - Si se recibe `401` en el refresh → la sesión expiró completamente, redirigir al login.
> - El `tokenType` siempre es `"Bearer"`.

---

---

## 2–8. Módulos Futuros (DTOs Preparados)

Los siguientes módulos tienen sus **DTOs, mappers y repositorios JPA completamente implementados**, pero aún no tienen controllers REST activos. Se documentan los DTOs de request para referencia del frontend.

---

### 📥 DTO: UserUpdateRequest (Identity)

| Campo      | Tipo   | Requerido | Validación     | Descripción            |
|------------|--------|-----------|----------------|------------------------|
| `fullName` | string | Sí        | max 150 chars  | Nombre completo        |
| `phone`    | string | No        | max 20 chars   | Número de teléfono     |

---

### 📥 DTO: CustomerAddressRequest (Identity)

| Campo         | Tipo   | Requerido | Validación     | Descripción                          |
|---------------|--------|-----------|----------------|--------------------------------------|
| `title`       | string | No        | max 50 chars   | Título de la dirección (ej: "Casa")  |
| `addressLine` | string | Sí        | No en blanco   | Dirección completa                   |
| `reference`   | string | No        | —              | Referencia adicional                 |
| `latitude`    | number | Sí        | Double          | Latitud (WGS84)                      |
| `longitude`   | number | Sí        | Double          | Longitud (WGS84)                     |

---

### 📥 DTO: SubscriptionPlanRequest (Subscription)

| Campo              | Tipo    | Requerido | Validación       | Descripción                   |
|--------------------|---------|-----------|------------------|-------------------------------|
| `name`             | string  | Sí        | max 100 chars    | Nombre del plan               |
| `price`            | number  | Sí        | ≥ 0.00           | Precio del plan               |
| `billingCycleDays` | number  | Sí        | ≥ 1              | Días del ciclo de facturación |
| `maxProducts`      | number  | Sí        | ≥ 1              | Máximo de productos           |
| `isActive`         | boolean | No        | —                | Estado del plan               |

---

### 📥 DTO: TenantCreateRequest (Tenant)

| Campo               | Tipo   | Requerido | Validación                  | Descripción                    |
|---------------------|--------|-----------|------------------------------|--------------------------------|
| `subscriptionId`    | UUID   | Sí        | UUID válido                  | ID de la suscripción           |
| `ownerId`           | UUID   | Sí        | UUID válido                  | ID del usuario propietario     |
| `name`              | string | Sí        | max 150 chars                | Nombre del restaurante         |
| `slug`              | string | Sí        | regex `^[a-z0-9-]+$`, max 100| Slug URL-friendly            |
| `logoUrl`           | string | No        | —                            | URL del logo                   |
| `bannerUrl`         | string | No        | —                            | URL del banner                 |
| `phone`             | string | No        | —                            | Teléfono del restaurante       |
| `address`           | string | No        | —                            | Dirección del restaurante      |
| `latitude`          | number | No        | Double                       | Latitud (WGS84)                |
| `longitude`         | number | No        | Double                       | Longitud (WGS84)               |
| `deliveryRadiusKm`  | number | No        | ≥ 0.00                       | Radio de delivery en km        |
| `defaultDeliveryFee`| number | No        | ≥ 0.00                       | Costo de delivery por defecto  |

---

### 📥 DTO: CategoryRequest (Catalog)

| Campo         | Tipo    | Requerido | Validación   | Descripción                  |
|---------------|---------|-----------|--------------|------------------------------|
| `name`        | string  | Sí        | max 100 chars| Nombre de la categoría       |
| `description` | string  | No        | —            | Descripción                  |
| `sortOrder`   | number  | No        | ≥ 0          | Orden de visualización       |
| `isActive`    | boolean | No        | —            | Estado activo/inactivo       |

---

### 📥 DTO: ProductRequest (Catalog)

| Campo         | Tipo    | Requerido | Validación    | Descripción                  |
|---------------|---------|-----------|---------------|------------------------------|
| `categoryId`  | UUID    | No        | UUID válido   | ID de la categoría           |
| `name`        | string  | Sí        | max 150 chars | Nombre del producto          |
| `description` | string  | No        | —             | Descripción                  |
| `price`       | number  | Sí        | ≥ 0.01        | Precio del producto          |
| `imageUrl`    | string  | No        | —             | URL de la imagen             |
| `isAvailable` | boolean | No        | —             | Disponibilidad del producto  |

---

### 📥 DTO: CartItemRequest (Cart)

| Campo       | Tipo   | Requerido | Validación  | Descripción           |
|-------------|--------|-----------|-------------|-----------------------|
| `productId` | UUID   | Sí        | UUID válido | ID del producto       |
| `quantity`  | number | Sí        | ≥ 1         | Cantidad              |
| `notes`     | string | No        | —           | Notas del item        |

---

### 📥 DTO: OrderCreateRequest (Order)

| Campo               | Tipo             | Requerido | Validación       | Descripción                      |
|---------------------|------------------|-----------|------------------|----------------------------------|
| `tenantId`          | UUID             | Sí        | UUID válido      | ID del restaurante               |
| `deliveryType`      | string           | Sí        | Enum válido      | `DELIVERY`, `TAKEAWAY`, `DINE_IN`|
| `customerAddressId` | UUID             | No        | UUID válido      | ID de la dirección del cliente   |
| `deliveryAddress`   | string           | No        | —                | Dirección de entrega en texto    |
| `deliveryLatitude`  | number           | No        | Double           | Latitud de entrega               |
| `deliveryLongitude` | number           | No        | Double           | Longitud de entrega              |
| `notes`             | string           | No        | —                | Notas del pedido                 |
| `items`             | OrderItemRequest[]| Sí       | min 1 item       | Items del pedido                 |

**Estructura de `OrderItemRequest`:**

| Campo       | Tipo   | Requerido | Validación  | Descripción           |
|-------------|--------|-----------|-------------|-----------------------|
| `productId` | UUID   | Sí        | UUID válido | ID del producto       |
| `quantity`  | number | Sí        | ≥ 1         | Cantidad              |
| `notes`     | string | No        | —           | Notas del item        |

**Ejemplo de request:**

```json
{
  "tenantId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "deliveryType": "DELIVERY",
  "customerAddressId": "f1e2d3c4-b5a6-7890-abcd-ef1234567890",
  "notes": "Sin cebolla por favor",
  "items": [
    {
      "productId": "11111111-2222-3333-4444-555555555555",
      "quantity": 2,
      "notes": "Bien cocida"
    },
    {
      "productId": "66666666-7777-8888-9999-000000000000",
      "quantity": 1
    }
  ]
}
```

---

### 📥 DTO: PaymentCreateRequest (Payment)

| Campo            | Tipo   | Requerido | Validación  | Descripción                          |
|------------------|--------|-----------|-------------|--------------------------------------|
| `orderId`        | UUID   | Sí        | UUID válido | ID del pedido                        |
| `tenantId`       | UUID   | Sí        | UUID válido | ID del restaurante                   |
| `mpPaymentId`    | string | No        | —           | ID de transacción de Mercado Pago    |
| `mpPreferenceId` | string | No        | —           | ID de preferencia de Checkout Pro    |
| `paymentMethod`  | string | No        | —           | Método de pago                       |
| `amount`         | number | Sí        | ≥ 0.01      | Monto del pago                       |
| `rawResponse`    | object | No        | JSON válido | Respuesta completa de MP (auditoría) |

---

---

## 📎 Notas Generales para el Frontend

1. **Autenticación Bearer Token:** Enviar el token en el header `Authorization: Bearer <accessToken>`, NO en cookies. Configurar en cada petición autenticada.

2. **Todos los IDs son UUID:** Tratarlos como `string` en el frontend. Formato: `3fa85f64-5717-4562-b3fc-2c963f66afa6`.

3. **Fechas en ISO 8601:** Todas las fechas llegan como strings ISO 8601 con offset (ej: `2026-09-26T14:00:00Z`). Nunca como timestamps numéricos.

4. **Envelopes consistentes:** Toda respuesta exitosa tiene `{ success: true, message, data }`. Toda respuesta de error tiene `{ message, error, status, timestamp, path, method }`.

5. **Rotación de Refresh Token:** Cada vez que se renueva el token, el servidor retorna **nuevos** access y refresh tokens. Siempre reemplazar ambos.

6. **Multi-tenant:** La mayoría de operaciones requieren un `tenantId` para saber en qué restaurante se opera.

7. **Coordenadas geoespaciales:** Usar `latitude` y `longitude` como `Double` (WGS84 SRID 4326). PostGIS valida que la dirección de entrega esté dentro del radio de delivery del restaurante.

8. **Snapshots históricos:** Los items de los pedidos guardan `productName` y `unitPrice` como snapshots inmutables. Incluso si el producto cambia de nombre o precio, el pedido mantiene la información original.

9. **Contraseña mínima:** 8 caracteres (no 6 como en otros sistemas).

10. **CORS configurado:** Los orígenes `http://localhost:3000` y `http://localhost:5173` están permitidos por defecto. En producción se configura mediante la variable de entorno `CORS_ALLOWED_ORIGINS`.

11. **Swagger UI:** Disponible en `/swagger-ui/index.html` para explorar y probar endpoints de forma visual e interactiva.

12. **Manejo de errores recomendado:**
    - `401` → Intentar renovar token con `/api/v1/auth/refresh`. Si falla, redirigir a login.
    - `403` → Mostrar mensaje de permisos insuficientes o cuenta desactivada.
    - `400` → Parsear el `message` para extraer los campos con error de validación.
    - `409` → Mostrar que el recurso ya existe (ej: email duplicado).
    - `404` → Mostrar mensaje de recurso no encontrado.
    - `500` → Mostrar mensaje genérico de error del servidor.
