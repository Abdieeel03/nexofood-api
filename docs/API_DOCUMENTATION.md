# 📖 NexoFood API — Documentación para Frontend

> **Proyecto:** NexoFood API — Plataforma SaaS Multi-Tenant para Restaurantes  
> **Stack:** Java 21 · Spring Boot 4.1.1 · PostgreSQL 16 + PostGIS · Hibernate Spatial  
> **Versión:** 1.0  
> **Última actualización:** 2026-11-10  
> **Guía de estilo:** [DOCUMENTATION_GUIDE.md](./DOCUMENTATION_GUIDE.md)

---

## 📌 Información General

| Campo                | Valor                                         |
| -------------------- | --------------------------------------------- |
| **Base URL (Local)** | `http://localhost:8080/api/v1`                |
| **Base URL (Prod)**  | `https://api.nexofood.lat/api/v1`             |
| **Formato**          | `application/json`                            |
| **Autenticación**    | JWT Bearer Token en header `Authorization`    |
| **Versión**          | `v1`                                          |
| **Swagger UI**       | `http://localhost:8080/swagger-ui/index.html` |
| **OpenAPI JSON**     | `http://localhost:8080/v3/api-docs`           |

---

## 🏪 Arquitectura Multi-Tenant y Tributaria

NexoFood es una plataforma **SaaS multi-tenant** donde cada restaurante es un **Tenant** independiente con configuración de marca, delivery y catálogo fiscal propio:

```
Usuario (User)
  └── Suscripción (Subscription) ← vinculada a un Plan SaaS
        └── Tenant (Restaurante con RUC propio) ← 1:1 con suscripción y owner
              ├── Configuración Tributaria (Taxes: IGV 18%, Exonerado, etc.)
              ├── Miembros (TenantMembers) con roles: OWNER, ADMIN, COCINERO, REPARTIDOR
              ├── Clientes de Tienda (TenantCustomer: puntos de lealtad, bloqueo, notas)
              ├── Catálogo: Categorías → Productos → Múltiples Precios/Promociones
              ├── Carritos (1 activo por cliente por restaurante)
              ├── Pedidos (Orders con RUC, desglose tributario, snapshot histórico inmutable)
              ├── Pagos (Mercado Pago Checkout Pro / Split Payment)
              └── Inventario (Items, Stock y Movimientos)
```

### Reglas Clave para el Frontend:

1. **Unicidad de Propietario:** Un usuario solo puede ser `OWNER` de un restaurante.
2. **Contexto Storefront:** Los clientes que compran en la tienda acceden vía `/api/v1/store/{tenantSlug}/auth/` para registrarse o iniciar sesión vinculados al restaurante.
3. **Carritos Aislados:** Cada cliente tiene un único carrito activo por cada restaurante (`tenantId, customerId`).
4. **Validación Geoespacial (PostGIS):** Al crear un pedido con entrega a domicilio (`DELIVERY`), el backend valida con SRID 4326 que las coordenadas del cliente se encuentren dentro del radio de entrega del restaurante (`deliveryRadiusKm`). Si está fuera de cobertura, responde con error `400 Bad Request`.
5. **Anti-Fraude en Cocina:** Los pedidos no avanzan al estado `EN_PREPARACION` sin que el pago esté confirmado (`APPROVED`).
6. **Sistema Tributario (SUNAT / Fiscal):** Cada producto está asociado a un impuesto (`taxId`). Los precios pueden ser inclusivos (ej. IGV 18% ya incluido en el precio de venta) o exclusivos. Las órdenes conservan un snapshot histórico inmutable del impuesto aplicado (`taxRate`, `taxAmount`) por ítem.
7. **Identificadores y Fechas:** Todos los IDs son **UUID** (strings de 36 caracteres). Las fechas se serializan en formato estándar **ISO 8601** (ej. `2026-10-06T14:30:00Z`).

---

## 🔑 Autenticación y Autorización

### Especificaciones del Token JWT:

- **Algoritmo:** HMAC-SHA256
- **Cabecera HTTP:** `Authorization: Bearer <accessToken>`
- **Access Token:** Expira en **15 minutos** (900 segundos).
- **Refresh Token:** Expira en **7 días**. Cuenta con **rotación obligatoria** en cada renovación.
- **Renovación:** Endpoint `POST /api/v1/auth/refresh` enviando el `refreshToken` en el cuerpo de la petición.
- **Expiración:** Si el token expira o es inválido, el servidor responde con `401 Unauthorized`.
- **Detección de Reutilización:** Si se intenta utilizar un refresh token revocado, el backend revoca **todos los tokens** activos del usuario por seguridad.

### Claims del Access Token

| Claim    | Tipo   | Descripción                                    |
| -------- | ------ | ---------------------------------------------- |
| `sub`    | string | Email del usuario                              |
| `role`   | string | Rol global del sistema (`SUPERADMIN` o `USER`) |
| `userId` | string | UUID del usuario                               |
| `iat`    | number | Timestamp UNIX de emisión                      |
| `exp`    | number | Timestamp UNIX de expiración                   |

### Roles de Staff en el Restaurante (`TenantStaffRole`):

- `OWNER`: Dueño y administrador total del restaurante.
- `ADMIN`: Administrador delegado con permisos de catálogo, miembros y pedidos.
- `COCINERO`: Visualización de pedidos en preparación y actualización de estado.
- `REPARTIDOR`: Gestión de pedidos en camino y confirmación de entrega.

## 📤 Envelopes de Respuesta

### ✅ Envelope de Éxito: `ApiResponse<T>`

Todas las respuestas exitosas de la API están envueltas en este formato uniforme:

```json
{
  "success": true,
  "message": "Operación realizada con éxito",
  "data": { ... }
}
```

| Campo     | Tipo    | Descripción                                          |
| --------- | ------- | ---------------------------------------------------- |
| `success` | boolean | Siempre `true` en peticiones exitosas                |
| `message` | string  | Mensaje descriptivo para UI o logs                   |
| `data`    | T       | Payload de la respuesta (objeto, array o paginación) |

### ❌ Envelope de Error: `ErrorResponse`

Todas las respuestas de error (4xx y 5xx) siguen esta estructura:

```json
{
  "message": "Descripción comprensible del error",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-10-06T14:00:00",
  "path": "/api/v1/ruta",
  "method": "POST"
}
```

| Campo       | Tipo   | Descripción                                              |
| ----------- | ------ | -------------------------------------------------------- |
| `message`   | string | Mensaje legible del error (incluye fallos de validación) |
| `error`     | string | Código o nombre del error HTTP                           |
| `status`    | number | Código de estado HTTP                                    |
| `timestamp` | string | Fecha y hora de generación (ISO 8601 local)              |
| `path`      | string | Ruta del endpoint invocado                               |
| `method`    | string | Método HTTP utilizado                                    |

### Códigos de Estado HTTP

| Código | Error                 | Descripción                                                     |
| ------ | --------------------- | --------------------------------------------------------------- |
| `200`  | OK                    | Consulta, actualización o autenticación completada exitosamente |
| `201`  | Created               | Recurso creado exitosamente                                     |
| `400`  | Bad Request           | Error de validación de campos o reglas de negocio infringidas   |
| `401`  | Unauthorized          | Token ausente, expirado, inválido o credenciales erróneas       |
| `403`  | Forbidden             | Sin permisos suficientes para la acción o cuenta desactivada    |
| `404`  | Not Found             | El recurso solicitado no existe                                 |
| `409`  | Conflict              | Conflicto de unicidad (email, slug o relación 1:1 duplicada)    |
| `500`  | Internal Server Error | Error no controlado en el servidor                              |

---

## 🟢 Estado Actual de los Módulos

| Bounded Context       | Estado               | Controladores Activos                                                                                                          |
| --------------------- | -------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| **1. Identity**       | ✅ Activo y testeado | `AuthController`, `UserController`, `CustomerAddressController`, `AdminUserController`                                         |
| **2. Store / Tenant** | ✅ Activo y testeado | `TenantController`, `TenantMemberController`, `TenantCustomerController`, `StoreAuthController`, `StorefrontCatalogController` |
| **3. Catalog & Tax**  | ✅ Activo y testeado | `TaxController`, `CategoryController`, `ProductController`, `ProductPriceController`                                           |
| **4. Subscription**   | ✅ Activo y testeado | `SubscriptionPlanController`, `AdminSubscriptionPlanController`, `SubscriptionController`                                      |
| **5. Cart**           | ✅ Activo y testeado | `CartController` (`/api/v1/store/{slug}/cart/**`)                                                                              |
| **6. Order**          | ✅ Activo y testeado | `StorefrontOrderController`, `OrderController`                                                                                 |
| **7. Payment**        | ✅ Activo y testeado | `MercadoPagoConnectionController`, `MercadoPagoOAuthCallbackController`, `MercadoPagoWebhookController`, `StorefrontPaymentController`, `TenantPaymentController` |
| **8. Inventory**      | ✅ Activo y testeado | `InventoryItemController`, `InventoryStockController`, `InventoryMovementController`, `ProductIngredientController`           |
| **9. Tracking**       | ✅ Activo y testeado | `DeliveryTrackingController`, `StorefrontTrackingController`                                                                  |

---

# 📡 Endpoints de la API

---

## 1. Identity — Autenticación y Perfil Global

Endpoints para gestión de usuarios globales en la plataforma.

---

### POST `/api/v1/auth/register`

> Registra una cuenta de usuario global en la plataforma y entrega tokens de sesión iniciales.

**Autenticación:** No requerida  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

#### 📥 Request

**Body (JSON):**

| Campo      | Tipo   | Requerido | Validación                   | Descripción              |
| ---------- | ------ | --------- | ---------------------------- | ------------------------ |
| `email`    | string | Sí        | Email válido, máx. 255 chars | Correo electrónico único |
| `password` | string | Sí        | Mín. 8, máx. 100 caracteres  | Contraseña de la cuenta  |
| `fullName` | string | Sí        | Máx. 150 caracteres          | Nombre completo          |
| `phone`    | string | No        | Máx. 20 caracteres           | Teléfono de contacto     |

**Ejemplo:**

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
      "createdAt": "2026-10-06T14:00:00Z",
      "updatedAt": "2026-10-06T14:00:00Z"
    }
  }
}
```

---

### POST `/api/v1/auth/login`

> Inicia sesión verificando credenciales globales. Revoca tokens previos y emite una nueva pareja de tokens.

**Autenticación:** No requerida  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

#### 📥 Request

**Body (JSON):**

| Campo      | Tipo   | Requerido | Validación   | Descripción            |
| ---------- | ------ | --------- | ------------ | ---------------------- |
| `email`    | string | Sí        | Email válido | Correo electrónico     |
| `password` | string | Sí        | No en blanco | Contraseña del usuario |

**Ejemplo:**

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
      "createdAt": "2026-10-06T14:00:00Z",
      "updatedAt": "2026-10-06T14:00:00Z"
    }
  }
}
```

---

### POST `/api/v1/auth/refresh`

> Renueva el token de acceso utilizando un refresh token válido. Implementa rotación obligatoria.

**Autenticación:** No requerida  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

#### 📥 Request

**Body (JSON):**

| Campo          | Tipo   | Requerido | Validación   | Descripción                          |
| -------------- | ------ | --------- | ------------ | ------------------------------------ |
| `refreshToken` | string | Sí        | No en blanco | Refresh token vigente recibido antes |

**Ejemplo:**

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
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... (nuevo rotado)",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "email": "carlos@ejemplo.com",
      "fullName": "Carlos Rodríguez",
      "phone": "+51999888777",
      "systemRole": "USER",
      "isActive": true,
      "createdAt": "2026-10-06T14:00:00Z",
      "updatedAt": "2026-10-06T14:00:00Z"
    }
  }
}
```

---

### GET `/api/v1/auth/me`

> Obtiene el perfil completo del usuario autenticado, incluyendo sus membresías de staff activas y sus direcciones de entrega guardadas.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquier usuario autenticado

#### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Perfil obtenido exitosamente",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "carlos@ejemplo.com",
    "fullName": "Carlos Rodríguez",
    "phone": "+51999888777",
    "systemRole": "USER",
    "isActive": true,
    "staffMemberships": [
      {
        "tenantId": "b1a2c3d4-e5f6-7890-abcd-ef1234567890",
        "tenantName": "Pizzería Nápoles",
        "tenantSlug": "pizzeria-napoles",
        "staffRole": "OWNER"
      }
    ],
    "addresses": [
      {
        "id": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
        "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "title": "Casa",
        "addressLine": "Av. Las Palmeras 123, Los Olivos",
        "reference": "Frente al parque principal",
        "latitude": -11.993452,
        "longitude": -77.072341,
        "createdAt": "2026-10-06T14:00:00Z",
        "updatedAt": "2026-10-06T14:00:00Z"
      }
    ]
  }
}
```

---

### 👤 Perfil y Cuenta de Usuario (`UserController`)

Ruta base: `/api/v1/users/me`

---

#### PUT `/api/v1/users/me`

> Actualiza los datos de perfil (nombre completo y teléfono) del usuario autenticado actual.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquier usuario autenticado  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON):**

| Campo      | Tipo   | Requerido | Validación          | Descripción                     |
| ---------- | ------ | --------- | ------------------- | ------------------------------- |
| `fullName` | string | Sí        | Máx. 150 caracteres | Nombre completo del usuario     |
| `phone`    | string | No        | Máx. 20 caracteres  | Número de teléfono de contacto  |

```json
{
  "fullName": "Carlos Rodríguez Pérez",
  "phone": "+51999888777"
}
```

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Perfil actualizado exitosamente",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "carlos@ejemplo.com",
    "fullName": "Carlos Rodríguez Pérez",
    "phone": "+51999888777",
    "systemRole": "USER",
    "isActive": true,
    "createdAt": "2026-10-06T14:00:00Z",
    "updatedAt": "2026-11-10T14:30:00Z"
  }
}
```

---

#### PATCH `/api/v1/users/me/password`

> Cambia la contraseña del usuario autenticado. Valida la contraseña actual y, por seguridad, revoca de inmediato todas las sesiones activas (`tokenVersion` se incrementa).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquier usuario autenticado  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON):**

| Campo             | Tipo   | Requerido | Validación        | Descripción                         |
| ----------------- | ------ | --------- | ----------------- | ----------------------------------- |
| `currentPassword` | string | Sí        | No vacío          | Contraseña actual de la cuenta      |
| `newPassword`     | string | Sí        | Entre 8 y 100 car | Nueva contraseña segura             |

```json
{
  "currentPassword": "PasswordAntiguo123!",
  "newPassword": "NuevaPasswordFuerte2026#"
}
```

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Contraseña actualizada exitosamente"
}
```

---

#### DELETE `/api/v1/users/me`

> Realiza la desactivación lógica (*soft delete*) de la cuenta del usuario autenticado y revoca inmediatamente todos sus tokens activos.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquier usuario autenticado

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Cuenta eliminada exitosamente"
}
```

---

### 📍 Direcciones de Entrega del Cliente (`CustomerAddressController`)

Ruta base: `/api/v1/users/me/addresses`

---

#### GET `/api/v1/users/me/addresses`

> Lista todas las direcciones de entrega guardadas del usuario autenticado con sus coordenadas geográficas WGS84 (SRID 4326).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquier usuario autenticado

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Direcciones obtenidas exitosamente",
  "data": [
    {
      "id": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "title": "Casa",
      "addressLine": "Av. Las Palmeras 123, Los Olivos",
      "reference": "Frente al parque principal",
      "latitude": -11.993452,
      "longitude": -77.072341,
      "createdAt": "2026-10-06T14:00:00Z",
      "updatedAt": "2026-10-06T14:00:00Z"
    }
  ]
}
```

---

#### POST `/api/v1/users/me/addresses`

> Registra una nueva dirección de entrega para el usuario autenticado con validación de coordenadas WGS84.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquier usuario autenticado  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON):**

| Campo         | Tipo   | Requerido | Validación         | Descripción                                    |
| ------------- | ------ | --------- | ------------------ | ---------------------------------------------- |
| `title`       | string | No        | Máx. 50 caracteres | Etiqueta identificadora (ej. "Casa", "Trabajo")|
| `addressLine` | string | Sí        | No vacío           | Dirección completa legible                     |
| `reference`   | string | No        | Texto libre        | Referencia para el repartidor                  |
| `latitude`    | number | Sí        | Entre -90.0 y 90.0 | Latitud WGS84                                  |
| `longitude`   | number | Sí        | Entre -180 y 180.0 | Longitud WGS84                                 |

```json
{
  "title": "Oficina",
  "addressLine": "Calle Las Begonias 441, San Isidro",
  "reference": "Piso 8, timbre NexoFood",
  "latitude": -12.093124,
  "longitude": -77.026451
}
```

##### 📤 Response

**Éxito (201 Created):**

```json
{
  "success": true,
  "message": "Dirección creada exitosamente",
  "data": {
    "id": "d2b3c4d5-e6f7-8901-bcde-fa2345678901",
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "title": "Oficina",
    "addressLine": "Calle Las Begonias 441, San Isidro",
    "reference": "Piso 8, timbre NexoFood",
    "latitude": -12.093124,
    "longitude": -77.026451,
    "createdAt": "2026-11-10T14:35:00Z",
    "updatedAt": "2026-11-10T14:35:00Z"
  }
}
```

---

#### PUT `/api/v1/users/me/addresses/{addressId}`

> Actualiza los datos o coordenadas de una dirección guardada existente del usuario.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Propietario de la dirección

##### 📤 Response

**Éxito (200 OK):** Retorna `ApiResponse<CustomerAddressResponse>`.

---

#### DELETE `/api/v1/users/me/addresses/{addressId}`

> Elimina de forma definitiva una dirección de entrega guardada del usuario.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Propietario de la dirección

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Dirección eliminada exitosamente"
}
```

---

### 🛡️ Administración de Usuarios Globales (`AdminUserController`)

Ruta base: `/api/v1/admin/users`

---

#### GET `/api/v1/admin/users`

> Lista de manera paginada todos los usuarios registrados en la plataforma.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `SUPERADMIN`

##### 📤 Response

**Éxito (200 OK):** Retorna `ApiResponse<PageResponse<UserResponse>>`.

---

#### PATCH `/api/v1/admin/users/{userId}/status`

> Activa o desactiva la cuenta de un usuario global en la plataforma. Si se desactiva, se invalidan de inmediato sus sesiones activas.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

```json
{
  "isActive": false
}
```

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Usuario desactivado",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "carlos@ejemplo.com",
    "fullName": "Carlos Rodríguez Pérez",
    "phone": "+51999888777",
    "systemRole": "USER",
    "isActive": false,
    "createdAt": "2026-10-06T14:00:00Z",
    "updatedAt": "2026-11-10T14:40:00Z"
  }
}
```

---

## 2. Store / Tenant — Gestión de Restaurantes, Staff y Clientes

Módulo central para la administración de restaurantes, asignación de colaboradores y gestión de clientes de tienda.

---

### 🏪 Gestión del Restaurante (`TenantController`)

---

#### POST `/api/v1/tenants`

> Registra un nuevo restaurante. El propietario (owner) se extrae directamente del token JWT y la suscripción se vincula automáticamente buscando la suscripción activa o trial vigente del usuario (opcionalmente se puede pasar subscriptionId).
>
> ⚠️ **Validación de Suscripción:** Es estrictamente obligatorio contar con una suscripción activa o período de prueba (TRIAL) vigente. Si el usuario intenta registrar un restaurante sin suscripción, la API retorna `400 Bad Request` con el mensaje: *"No cuentas con una suscripción activa para crear un restaurante"*.

**Autenticación:** Requerida (Bearer <token>)  
**Rol requerido:** USER (con suscripción activa o trial vigente)  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON):**

| Campo                | Tipo       | Requerido | Validación                                | Descripción                                             |
| -------------------- | ---------- | --------- | ----------------------------------------- | ------------------------------------------------------- |
| subscriptionId       | UUID       | No        | UUID válido                               | Opcional: ID de la suscripción (se infiere si se omite) |
| `name`               | string     | Sí        | Máx. 150 caracteres                       | Nombre comercial                                        |
| `slug`               | string     | Sí        | Regex `^[a-z0-9-]+$`, máx. 100 caracteres | Slug para la URL del storefront                         |
| `logoUrl`            | string     | No        | URL válida                                | Logo de la tienda                                       |
| `bannerUrl`          | string     | No        | URL válida                                | Banner publicitario                                     |
| `phone`              | string     | No        | Máx. 20 caracteres                        | Teléfono del restaurante                                |
| `address`            | string     | No        | Texto descriptivo                         | Dirección física del local                              |
| `latitude`           | number     | No        | WGS84 (-90 a 90)                          | Latitud del local                                       |
| `longitude`          | number     | No        | WGS84 (-180 a 180)                        | Longitud del local                                      |
| `deliveryRadiusKm`   | BigDecimal | No        | ≥ 0.00 (default: 5.00)                    | Radio de reparto en kilómetros                          |
| `defaultDeliveryFee` | BigDecimal | No        | ≥ 0.00 (default: 0.00)                    | Tarifa de envío estándar                                |

**Ejemplo:**

```json
{
  "name": "Burger Bros",
  "slug": "burger-bros",
  "logoUrl": "https://storage.nexofood.lat/logos/burger.png",
  "bannerUrl": "https://storage.nexofood.lat/banners/burger.png",
  "phone": "+51987654321",
  "address": "Av. Larco 456, Miraflores",
  "latitude": -12.121543,
  "longitude": -77.028941,
  "deliveryRadiusKm": 6.5,
  "defaultDeliveryFee": 5.0
}
```

##### 📤 Response

**Éxito (201 Created):**

```json
{
  "success": true,
  "message": "Restaurante creado exitosamente",
  "data": {
    "id": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "subscriptionId": "8fa85f64-5717-4562-b3fc-2c963f66afa1",
    "ownerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "Burger Bros",
    "slug": "burger-bros",
    "logoUrl": "https://storage.nexofood.lat/logos/burger.png",
    "bannerUrl": "https://storage.nexofood.lat/banners/burger.png",
    "phone": "+51987654321",
    "address": "Av. Larco 456, Miraflores",
    "latitude": -12.121543,
    "longitude": -77.028941,
    "deliveryRadiusKm": 6.5,
    "defaultDeliveryFee": 5.0,
    "isMpConnected": false,
    "onlinePaymentsEnabled": false,
    "mpUserId": null,
    "mpConnectedAt": null,
    "mpTokenExpiresAt": null,
    "isActive": true,
    "createdAt": "2026-10-06T14:00:00Z",
    "updatedAt": "2026-10-06T14:00:00Z"
  }
}
```

---

#### GET `/api/v1/tenants/{id}`

> Obtiene los datos detallados de un restaurante por su identificador único UUID. Protegido por membresía en el restaurante.

**Autenticación:** Requerida (Bearer <token>)  
**Rol requerido:** Miembro activo del restaurante (OWNER, ADMIN, COCINERO, REPARTIDOR) o SUPERADMIN

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Restaurante obtenido exitosamente",
  "data": {
    "id": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Burger Bros",
    "slug": "burger-bros",
    "deliveryRadiusKm": 6.5,
    "defaultDeliveryFee": 5.0,
    "isActive": true
  }
}
```

---

#### GET `/api/v1/tenants/slug/{slug}`

> Obtiene la información pública de un restaurante a través de su slug (utilizado por el storefront público para cargar el menú y la tienda sin necesidad de token).

**Autenticación:** No requerida (Endpoint Público)  
**Rol requerido:** Ninguno

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Restaurante obtenido exitosamente",
  "data": {
    "id": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Burger Bros",
    "slug": "burger-bros",
    "logoUrl": "https://storage.nexofood.lat/logos/burger.png",
    "bannerUrl": "https://storage.nexofood.lat/banners/burger.png",
    "phone": "+51987654321",
    "address": "Av. Larco 456, Miraflores",
    "latitude": -12.121543,
    "longitude": -77.028941,
    "deliveryRadiusKm": 6.5,
    "defaultDeliveryFee": 5.0,
    "isMpConnected": true,
    "isActive": true
  }
}
```

---

#### PUT `/api/v1/tenants/{id}`

> Actualiza la información operativa y comercial del restaurante.

**Autenticación:** Requerida (Bearer <token>)  
**Rol requerido:** OWNER del restaurante o SUPERADMIN  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON):**

| Campo                | Tipo       | Requerido | Validación          | Descripción         |
| -------------------- | ---------- | --------- | ------------------- | ------------------- |
| `name`               | string     | Sí        | Máx. 150 caracteres | Nombre comercial    |
| `logoUrl`            | string     | No        | —                   | Logo                |
| `bannerUrl`          | string     | No        | —                   | Banner              |
| `phone`              | string     | No        | Máx. 20 caracteres  | Teléfono            |
| `address`            | string     | No        | —                   | Dirección           |
| `latitude`           | number     | No        | Double              | Latitud             |
| `longitude`          | number     | No        | Double              | Longitud            |
| `deliveryRadiusKm`   | BigDecimal | No        | ≥ 0.00              | Radio de cobertura  |
| `defaultDeliveryFee` | BigDecimal | No        | ≥ 0.00              | Costo de envío base |
| `isActive`           | boolean    | No        | —                   | Estado              |

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Restaurante actualizado exitosamente",
  "data": {
    "id": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Burger Bros Gourmet",
    "slug": "burger-bros",
    "deliveryRadiusKm": 7.0,
    "defaultDeliveryFee": 6.0,
    "isActive": true
  }
}
```

---

#### PATCH `/api/v1/tenants/{id}/status`

> Activa o desactiva las operaciones del restaurante mediante el DTO validado TenantStatusRequest.

**Autenticación:** Requerida (Bearer <token>)  
**Rol requerido:** OWNER del restaurante o SUPERADMIN  
**Content-Type:** `application/json`

##### 📥 Request

```json
{
  "isActive": false
}
```

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Estado del restaurante actualizado",
  "data": null
}
```

---

#### GET `/api/v1/tenants/me`

> Obtiene el restaurante asociado al usuario autenticado actual como propietario o miembro de staff. Si el usuario aún no tiene un restaurante asociado, la API responde con `404 Not Found` en lugar de devolver un objeto `data: null`, permitiendo al frontend capturar la excepción y dirigir al usuario al onboarding o flujo de creación de tienda.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquier usuario autenticado

##### 📤 Response

**Caso 1: Con restaurante vinculado (200 OK):**

```json
{
  "success": true,
  "message": "Restaurante del usuario obtenido exitosamente",
  "data": {
    "id": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "subscriptionId": "8fa85f64-5717-4562-b3fc-2c963f66afa1",
    "ownerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "Burger Bros",
    "slug": "burger-bros",
    "logoUrl": "https://storage.nexofood.lat/logos/burger.png",
    "bannerUrl": "https://storage.nexofood.lat/banners/burger.png",
    "phone": "+51987654321",
    "address": "Av. Larco 456, Miraflores",
    "latitude": -12.121543,
    "longitude": -77.028941,
    "deliveryRadiusKm": 6.5,
    "defaultDeliveryFee": 5.0,
    "isMpConnected": false,
    "onlinePaymentsEnabled": false,
    "mpUserId": null,
    "mpConnectedAt": null,
    "mpTokenExpiresAt": null,
    "isActive": true,
    "createdAt": "2026-10-06T14:00:00Z",
    "updatedAt": "2026-10-06T14:00:00Z"
  }
}
```

**Caso 2: Sin restaurante vinculado (404 Not Found):**

```json
{
  "message": "No tienes ninguna tienda asociada",
  "error": "Not Found",
  "status": 404,
  "timestamp": "2026-11-10T15:00:00",
  "path": "/api/v1/tenants/me",
  "method": "GET"
}
```

---

### 👥 Equipo / Staff (`TenantMemberController`)

Ruta base: `/api/v1/tenants/{tenantId}/members`

---

#### GET `/api/v1/tenants/{tenantId}/members`

> Lista todos los miembros del equipo del restaurante con sus roles y estados.

**Autenticación:** Requerida  
**Rol requerido:** `OWNER` o `ADMIN`

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Miembros obtenidos exitosamente",
  "data": [
    {
      "id": "f1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "userFullName": "Carlos Rodríguez",
      "userEmail": "carlos@ejemplo.com",
      "role": "OWNER",
      "isActive": true,
      "createdAt": "2026-10-06T14:00:00Z",
      "updatedAt": "2026-10-06T14:00:00Z"
    },
    {
      "id": "f2a2c3d4-e5f6-7890-abcd-ef1234567890",
      "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "userId": "4fa85f64-5717-4562-b3fc-2c963f66afa7",
      "userFullName": "Ana Gómez",
      "userEmail": "ana@ejemplo.com",
      "role": "COCINERO",
      "isActive": true,
      "createdAt": "2026-10-06T14:00:00Z",
      "updatedAt": "2026-10-06T14:00:00Z"
    }
  ]
}
```

---

#### POST `/api/v1/tenants/{tenantId}/members`

> Agrega un usuario existente como empleado del restaurante con un rol específico.

**Autenticación:** Requerida  
**Rol requerido:** `OWNER` o `ADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON):**

| Campo    | Tipo   | Requerido | Validación                                 | Descripción            |
| -------- | ------ | --------- | ------------------------------------------ | ---------------------- |
| `userId` | UUID   | Sí        | UUID válido                                | ID del usuario global  |
| `role`   | string | Sí        | `OWNER`, `ADMIN`, `COCINERO`, `REPARTIDOR` | Rol operativo asignado |

**Ejemplo:**

```json
{
  "userId": "4fa85f64-5717-4562-b3fc-2c963f66afa7",
  "role": "COCINERO"
}
```

##### 📤 Response

**Éxito (201 Created):**

```json
{
  "success": true,
  "message": "Miembro agregado exitosamente",
  "data": {
    "id": "f2a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "userId": "4fa85f64-5717-4562-b3fc-2c963f66afa7",
    "userFullName": "Ana Gómez",
    "userEmail": "ana@ejemplo.com",
    "role": "COCINERO",
    "isActive": true
  }
}
```

---

#### PATCH `/api/v1/tenants/{tenantId}/members/{memberId}/role`

> Modifica el rol asignado a un empleado del restaurante mediante el DTO validado MemberRoleUpdateRequest. El OWNER no puede ser modificado por un ADMIN.

**Autenticación:** Requerida (Bearer <token>)  
**Rol requerido:** OWNER o ADMIN  
**Content-Type:** `application/json`

##### 📥 Request

```json
{
  "role": "ADMIN"
}
```

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Rol actualizado exitosamente",
  "data": {
    "id": "f2a2c3d4-e5f6-7890-abcd-ef1234567890",
    "role": "ADMIN"
  }
}
```

---

#### DELETE `/api/v1/tenants/{tenantId}/members/{memberId}`

> Desvincula o retira a un empleado del restaurante.

**Autenticación:** Requerida  
**Rol requerido:** `OWNER`

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Miembro desvinculado exitosamente",
  "data": null
}
```

---

### 🛍️ Clientes de Tienda (`TenantCustomerController` - Panel Admin)

Ruta base: `/api/v1/admin/tenants/{tenantId}/customers`

---

#### GET `/api/v1/admin/tenants/{tenantId}/customers`

> Lista todos los clientes registrados en esta tienda con soporte de paginación de Spring Data.

**Autenticación:** Requerida  
**Rol requerido:** `OWNER` o `ADMIN`  
**Query Parameters:**

- `page`: Número de página (0-indexed, default: 0)
- `size`: Tamaño de página (default: 20)
- `sort`: Campo de ordenamiento (ej. `createdAt,desc`)

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Clientes obtenidos exitosamente",
  "data": {
    "content": [
      {
        "id": "d1a2c3d4-e5f6-7890-abcd-ef1234567890",
        "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
        "tenantName": "Burger Bros",
        "tenantSlug": "burger-bros",
        "userId": "5fa85f64-5717-4562-b3fc-2c963f66afa8",
        "userFullName": "Lucía Morales",
        "userEmail": "lucia@ejemplo.com",
        "loyaltyPoints": 150,
        "isBlocked": false,
        "notes": "Cliente frecuente, suele pedir término medio",
        "totalOrders": 12,
        "firstOrderAt": "2026-08-10T19:30:00Z",
        "lastOrderAt": "2026-10-05T21:15:00Z",
        "createdAt": "2026-08-10T19:00:00Z",
        "updatedAt": "2026-10-05T21:15:00Z"
      }
    ],
    "page": {
      "size": 20,
      "number": 0,
      "totalElements": 1,
      "totalPages": 1
    }
  }
}
```

---

#### GET `/api/v1/admin/tenants/{tenantId}/customers/{customerId}`

> Obtiene el perfil del cliente, su historial de pedidos y puntos de lealtad en este restaurante.

**Autenticación:** Requerida  
**Rol requerido:** `OWNER` o `ADMIN`

---

#### PATCH `/api/v1/admin/tenants/{tenantId}/customers/{customerId}/block`

> Bloquea o desbloquea a un cliente para impedir pedidos en esta tienda.

**Autenticación:** Requerida  
**Rol requerido:** `OWNER` o `ADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

```json
{
  "isBlocked": true
}
```

##### 📤 Response (200 OK)

```json
{
  "success": true,
  "message": "Cliente bloqueado",
  "data": {
    "id": "d1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "isBlocked": true
  }
}
```

---

#### PATCH `/api/v1/admin/tenants/{tenantId}/customers/{customerId}/notes`

> Guarda o actualiza notas privadas del restaurante sobre un cliente (visibles solo para el staff).

##### 📥 Request

```json
{
  "notes": "Cliente VIP, verificar packaging cuidadoso"
}
```

##### 📤 Response (200 OK)

```json
{
  "success": true,
  "message": "Notas actualizadas exitosamente",
  "data": { ... }
}
```

---

#### PATCH `/api/v1/admin/tenants/{tenantId}/customers/{customerId}/points`

> Ajusta manualmente el saldo de puntos de fidelidad del cliente (suma o resta).

##### 📥 Request

```json
{
  "adjustment": 50
}
```

##### 📤 Response (200 OK)

```json
{
  "success": true,
  "message": "Puntos ajustados exitosamente",
  "data": {
    "loyaltyPoints": 200
  }
}
```

---

### 🌐 Storefront Auth Contextual (`StoreAuthController`)

Ruta base: `/api/v1/store/{tenantSlug}/auth`

Endpoints diseñados para el storefront web/móvil donde los clientes compran. Permiten registrarse e iniciar sesión asociando al cliente directamente con el restaurante del slug.

---

#### POST `/api/v1/store/{tenantSlug}/auth/register`

> Registra a un nuevo cliente en el contexto de la tienda. Crea el usuario global y la ficha de cliente (`TenantCustomer`). Si el email ya existía, pide login.

**Autenticación:** No requerida (Público)  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON):**

| Campo      | Tipo   | Requerido | Validación          | Descripción          |
| ---------- | ------ | --------- | ------------------- | -------------------- |
| `fullName` | string | Sí        | Máx. 150 caracteres | Nombre completo      |
| `email`    | string | Sí        | Email válido        | Correo electrónico   |
| `password` | string | Sí        | Mínimo 8 caracteres | Contraseña           |
| `phone`    | string | No        | Máx. 20 caracteres  | Teléfono de contacto |

**Ejemplo:**

```json
{
  "fullName": "Mariana Vega",
  "email": "mariana@ejemplo.com",
  "password": "Password123!",
  "phone": "+51999111222"
}
```

##### 📤 Response

**Éxito (201 Created):**

```json
{
  "success": true,
  "message": "Registro exitoso en la tienda",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "7fa85f64-5717-4562-b3fc-2c963f66afa9",
      "email": "mariana@ejemplo.com",
      "fullName": "Mariana Vega",
      "systemRole": "USER",
      "isActive": true
    },
    "customerProfile": {
      "id": "d2a2c3d4-e5f6-7890-abcd-ef1234567890",
      "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "tenantName": "Burger Bros",
      "tenantSlug": "burger-bros",
      "loyaltyPoints": 0,
      "isBlocked": false,
      "totalOrders": 0
    }
  }
}
```

---

#### POST `/api/v1/store/{tenantSlug}/auth/login`

> Autentica con credenciales globales y retorna el token y el perfil contextual del cliente en esta tienda específica.

**Autenticación:** No requerida (Público)  
**Content-Type:** `application/json`

##### 📥 Request

```json
{
  "email": "mariana@ejemplo.com",
  "password": "Password123!"
}
```

##### 📤 Response (200 OK)

Retorna la estructura `StoreAuthResponse` con tokens, `user` y `customerProfile`.

---

## 3. Catalog & Tributos — Categorías, Impuestos, Productos y Menú Público

Este módulo gestiona la oferta de productos, listas de precios dinámicos, horarios de promoción, esquema fiscal aplicable y el menú público del storefront.

> **🏪 Multi-Tenant & Seguridad:**
>
> - **Consultas de Backoffice (`GET /api/v1/tenants/{tenantId}/**`):** Protegidas con `@PreAuthorize("@tenantAccess.isMember(#tenantId)")`, permitiendo el acceso a **todo el staff activo** del restaurante (`OWNER`, `ADMIN`, `COCINERO`, `REPARTIDOR`) y a `SUPERADMIN`.
> - **Mutaciones de Backoffice (`POST`, `PUT`, `PATCH`, `DELETE` en `/api/v1/tenants/{tenantId}/**`):** Protegidas con `@PreAuthorize("@tenantAccess.hasAnyRole(#tenantId, OWNER, ADMIN)")`(restringido a`OWNER`, `ADMIN`y`SUPERADMIN`).
> - **Storefront para Clientes (`/api/v1/store/{slug}/menu`, `/categories`, `/products`, `/products/{id}`):** Endpoints **públicos** (`permitAll` en `SecurityConfig`) sin necesidad de token JWT.

---

### 🏛️ Gestión de Impuestos (`TaxController`)

Ruta base: `/api/v1/tenants/{tenantId}/taxes`

Cada restaurante puede configurar sus propios impuestos (ej. IGV 18%, Impuesto al Consumo, Exonerado 0%).

#### GET `/api/v1/tenants/{tenantId}/taxes`

> Lista los impuestos configurados en el restaurante.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)  
**Query Parameters:**

- `activeOnly` (boolean, default `false`): si es `true`, retorna únicamente los impuestos activos.

#### GET `/api/v1/tenants/{tenantId}/taxes/{taxId}`

> Obtiene el detalle de un impuesto del restaurante por su ID.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)

#### POST `/api/v1/tenants/{tenantId}/taxes`

> Crea un nuevo impuesto para el restaurante.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)  
**Content-Type:** `application/json`

#### PUT `/api/v1/tenants/{tenantId}/taxes/{taxId}`

> Actualiza un impuesto del restaurante. Si el impuesto ya está asignado a productos o ventas históricas, su tasa (`rate`) no puede modificarse (retorna `400 Bad Request`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)  
**Content-Type:** `application/json`

#### PATCH `/api/v1/tenants/{tenantId}/taxes/{taxId}/status`

> Activa o desactiva un impuesto mediante `TaxStatusRequest` (`{ "isActive": true }`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)  
**Content-Type:** `application/json`

#### DELETE `/api/v1/tenants/{tenantId}/taxes/{taxId}`

> Elimina un impuesto si no ha sido utilizado en productos ni órdenes históricas (de lo contrario retorna `400 Bad Request` indicando que debe desactivarse).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)

#### 📥 DTO: TaxRequest

| Campo         | Tipo       | Requerido | Validación                           | Descripción                                |
| ------------- | ---------- | --------- | ------------------------------------ | ------------------------------------------ |
| `name`        | string     | Sí        | Máx. 50 caracteres                   | Nombre del tributo (ej. "IGV 18%")         |
| `rate`        | BigDecimal | Sí        | ≥ 0.00, máx. 3 enteros y 2 decimales | Tasa porcentual (ej. 18.00)                |
| `code`        | string     | No        | Máx. 20 caracteres                   | Código oficial SUNAT / fiscal (ej. "1000") |
| `isInclusive` | boolean    | No        | Default: `true`                      | Si el impuesto está incluido en el precio  |
| `isActive`    | boolean    | No        | Default: `true`                      | Estado activo del impuesto                 |

#### 📤 DTO: TaxResponse

| Campo         | Tipo       | Nullable | Descripción                                |
| ------------- | ---------- | -------- | ------------------------------------------ |
| `id`          | UUID       | No       | Identificador único del impuesto           |
| `tenantId`    | UUID       | No       | Restaurante propietario                    |
| `name`        | string     | No       | Nombre del impuesto                        |
| `rate`        | BigDecimal | No       | Tasa del impuesto (ej. 18.00)              |
| `code`        | string     | Sí       | Código tributario fiscal                   |
| `isInclusive` | boolean    | No       | ¿Precio de lista ya incluye este impuesto? |
| `isActive`    | boolean    | No       | Estado                                     |
| `createdAt`   | string     | No       | Fecha de creación (ISO 8601)               |
| `updatedAt`   | string     | No       | Fecha de actualización (ISO 8601)          |

#### 💡 Reglas de Cálculo de Impuestos en NexoFood:

- **Impuesto Inclusivo (`isInclusive = true`, ej. IGV Perú):**
  $$\text{baseUnitPrice} = \frac{\text{unitPrice}}{1 + (\text{rate} / 100)}$$
  $$\text{taxAmount} = \text{unitPrice} - \text{baseUnitPrice}$$
- **Impuesto Exclusivo (`isInclusive = false`):**
  $$\text{baseUnitPrice} = \text{unitPrice}$$
  $$\text{taxAmount} = \text{unitPrice} \times (\text{rate} / 100)$$

---

### 📂 Categorías de Productos (`CategoryController`)

Ruta base: `/api/v1/tenants/{tenantId}/categories`

#### GET `/api/v1/tenants/{tenantId}/categories`

> Lista las categorías del restaurante ordenadas ascendentemente por `sortOrder`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)  
**Query Parameters:**

- `activeOnly` (boolean, default `false`): si es `true`, retorna solo las categorías activas.

#### GET `/api/v1/tenants/{tenantId}/categories/{categoryId}`

> Obtiene una categoría del restaurante por su ID.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)

#### POST `/api/v1/tenants/{tenantId}/categories`

> Crea una categoría en el restaurante. Valida que el nombre no esté duplicado dentro del mismo tenant (`409 Conflict`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)  
**Content-Type:** `application/json`

#### PUT `/api/v1/tenants/{tenantId}/categories/{categoryId}`

> Actualiza el nombre, descripción, orden o estado de una categoría.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)  
**Content-Type:** `application/json`

#### PATCH `/api/v1/tenants/{tenantId}/categories/{categoryId}/status`

> Activa o desactiva una categoría enviando `CategoryStatusRequest` (`{ "isActive": false }`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)  
**Content-Type:** `application/json`

#### PATCH `/api/v1/tenants/{tenantId}/categories/{categoryId}/sort-order`

> Cambia la posición de visualización (`sortOrder`) de una categoría enviando `CategorySortOrderRequest` (`{ "sortOrder": 2 }`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)  
**Content-Type:** `application/json`

#### DELETE `/api/v1/tenants/{tenantId}/categories/{categoryId}`

> Elimina una categoría del restaurante. Si la categoría tiene productos asociados, bloquea la operación con `400 Bad Request`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)

#### 📥 DTO: CategoryRequest

| Campo         | Tipo    | Requerido | Validación          | Descripción                    |
| ------------- | ------- | --------- | ------------------- | ------------------------------ |
| `name`        | string  | Sí        | Máx. 100 caracteres | Nombre de la categoría         |
| `description` | string  | No        | —                   | Descripción para la carta      |
| `sortOrder`   | number  | No        | ≥ 0                 | Orden para mostrar en menú     |
| `isActive`    | boolean | No        | Default: `true`     | Si se muestra en el storefront |

#### 📤 DTO: CategoryResponse

| Campo         | Tipo    | Nullable | Descripción                      |
| ------------- | ------- | -------- | -------------------------------- |
| `id`          | UUID    | No       | Identificador único              |
| `tenantId`    | UUID    | No       | Restaurante                      |
| `name`        | string  | No       | Nombre de la categoría           |
| `description` | string  | Sí       | Descripción                      |
| `sortOrder`   | number  | No       | Posición relativa en la interfaz |
| `isActive`    | boolean | No       | Estado                           |
| `createdAt`   | string  | No       | ISO 8601                         |
| `updatedAt`   | string  | No       | ISO 8601                         |

---

### 🍔 Productos (`ProductController`)

Ruta base: `/api/v1/tenants/{tenantId}/products`

Un producto cuenta con un precio base activo obligatorio y puede tener múltiples precios condicionales (`ProductPriceRequest`) para días especiales o rangos de horario. Al crear un producto se valida el límite `maxProducts` del plan de suscripción vigente del restaurante mediante `SubscriptionGuard`.

#### GET `/api/v1/tenants/{tenantId}/products`

> Lista paginada de productos del restaurante con filtros opcionales por categoría y disponibilidad. Retorna `ApiResponse<PageResponse<ProductResponse>>`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)  
**Query Parameters:**

- `categoryId` (UUID, opcional): filtra por categoría.
- `availableOnly` (boolean, opcional): si es `true`, retorna solo productos con `isAvailable = true`.
- `page` (int, default `0`), `size` (int, default `20`), `sort` (string, opcional).

#### GET `/api/v1/tenants/{tenantId}/products/{productId}`

> Obtiene el detalle completo de un producto del restaurante incluyendo todas sus reglas de precio configuradas.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)

#### POST `/api/v1/tenants/{tenantId}/products`

> Crea un nuevo producto en el catálogo validando que la suscripción esté activa y no supere el límite `maxProducts` del plan contratado.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)  
**Content-Type:** `application/json`

#### PUT `/api/v1/tenants/{tenantId}/products/{productId}`

> Actualiza los datos de un producto y su precio base (si se envía `price`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)  
**Content-Type:** `application/json`

#### PATCH `/api/v1/tenants/{tenantId}/products/{productId}/availability`

> Activa o desactiva la disponibilidad de un producto enviando `ProductAvailabilityRequest` (`{ "isAvailable": false }`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)  
**Content-Type:** `application/json`

#### DELETE `/api/v1/tenants/{tenantId}/products/{productId}`

> Deshabilita un producto mediante borrado lógico (`isAvailable = false`) preservando la integridad referencial histórica.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)

---

### 🏷️ Reglas de Precio y Promociones (`ProductPriceController`)

Ruta base: `/api/v1/tenants/{tenantId}/products/{productId}/prices`

Gestiona las reglas de precios de un producto garantizando:

1. Exactamente **un precio base activo** (`isBase = true`, `isActive = true`) por producto.
2. Coherencia en rangos de fechas (`startDate <= endDate`), horas (`startTime != endTime`, soportando horarios nocturnos que cruzan la medianoche), días ISO (`1=Lunes ... 7=Domingo`) y porcentaje de descuento (`0..100`).
3. Prohibición de **solapamientos ambiguos** entre dos reglas promocionales activas con la **misma prioridad** (`409 Conflict`).

#### GET `/api/v1/tenants/{tenantId}/products/{productId}/prices`

> Lista todas las reglas de precio de un producto.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)

#### GET `/api/v1/tenants/{tenantId}/products/{productId}/prices/{priceId}`

> Obtiene el detalle de una regla de precio específica del producto.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)

#### POST `/api/v1/tenants/{tenantId}/products/{productId}/prices`

> Agrega una nueva regla de precio o promoción al producto.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)  
**Content-Type:** `application/json`

#### PUT `/api/v1/tenants/{tenantId}/products/{productId}/prices/{priceId}`

> Actualiza una regla de precio existente del producto.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)  
**Content-Type:** `application/json`

#### DELETE `/api/v1/tenants/{tenantId}/products/{productId}/prices/{priceId}`

> Elimina una regla de precio promocional. No permite eliminar el precio base activo del producto (`400 Bad Request`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)

---

### 🌐 Menú Público del Storefront (`StorefrontCatalogController`)

Ruta base: `/api/v1/store/{slug}`

Endpoints públicos para que los comensales consulten la carta digital del restaurante con los precios dinámicos vigentes en tiempo real y el desglose tributario ya calculado.

#### GET `/api/v1/store/{slug}/menu`

> Retorna el menú público del restaurante activo identificado por su `slug`: categorías activas (ordenadas por `sortOrder`) junto con sus productos disponibles, precio vigente evaluado en el instante de la consulta, impuestos calculados y el indicador `onlinePaymentsEnabled` para habilitar o deshabilitar la pasarela Mercado Pago en la interfaz del storefront.

**Autenticación:** No requerida (Público)  
**Rol requerido:** Ninguno

##### 📤 Response (200 OK)

```json
{
  "success": true,
  "message": "Menú obtenido exitosamente",
  "data": {
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantName": "Burger Bros",
    "tenantSlug": "burger-bros",
    "logoUrl": "https://storage.nexofood.lat/logos/burger.png",
    "bannerUrl": "https://storage.nexofood.lat/banners/burger.png",
    "onlinePaymentsEnabled": true,
    "categories": [
      {
        "id": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
        "name": "Hamburguesas Clásicas",
        "description": "Las mejores hamburguesas artesanales",
        "sortOrder": 1,
        "products": [
          {
            "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
            "name": "Hamburguesa Doble Queso",
            "price": 24.90,
            "finalPrice": 24.90,
            "taxName": "IGV 18%",
            "taxRate": 18.00,
            "taxInclusive": true,
            "isAvailable": true
          }
        ]
      }
    ],
    "uncategorizedProducts": []
  }
}
```

#### GET `/api/v1/store/{slug}/categories`

> Lista las categorías activas del restaurante ordenadas ascendentemente por `sortOrder` para filtros de navegación en la tienda del cliente.

**Autenticación:** No requerida (Público)  
**Rol requerido:** Ninguno

#### GET `/api/v1/store/{slug}/products`

> Lista paginada de los productos disponibles del restaurante para el cliente (`PageResponse<StorefrontProductResponse>`), con soporte de filtrado por categoría activa y búsqueda por nombre.

**Autenticación:** No requerida (Público)  
**Rol requerido:** Ninguno  
**Query Parameters:**

- `categoryId` (UUID, opcional): filtra productos de una categoría activa.
- `search` (string, opcional): filtra productos por coincidencia parcial en el nombre (case-insensitive).
- `page` (int, default `0`), `size` (int, default `20`), `sort` (string, opcional).

#### GET `/api/v1/store/{slug}/products/{id}`

> Retorna el detalle público de un producto disponible en la tienda junto con su precio vigente, promoción activa (si aplica) y desglose de impuesto (`baseUnitPrice`, `taxAmount`, `finalPrice`).

**Autenticación:** No requerida (Público)  
**Rol requerido:** Ninguno

---

#### 📥 DTO: ProductRequest

| Campo         | Tipo                      | Requerido | Validación              | Descripción                             |
| ------------- | ------------------------- | --------- | ----------------------- | --------------------------------------- |
| `categoryId`  | UUID                      | No        | UUID válido             | Categoría asignada                      |
| `taxId`       | UUID                      | No        | UUID válido             | Impuesto tributario aplicable           |
| `name`        | string                    | Sí        | Máx. 150 caracteres     | Nombre del producto                     |
| `description` | string                    | No        | —                       | Descripción / ingredientes              |
| `price`       | BigDecimal                | Sí        | ≥ 0.01                  | Precio base de venta al público         |
| `imageUrl`    | string                    | No        | URL válida              | Foto del platillo                       |
| `isAvailable` | boolean                   | No        | Default: `true`         | Disponibilidad en cocina                |
| `prices`      | List<ProductPriceRequest> | No        | Lista de precios extras | Reglas de precios dinámicos/promociones |

#### 📥 DTO: ProductPriceRequest

| Campo                | Tipo       | Requerido | Validación           | Descripción                                       |
| -------------------- | ---------- | --------- | -------------------- | ------------------------------------------------- |
| `id`                 | UUID       | No        | —                    | ID si es actualización de precio existente        |
| `name`               | string     | Sí        | Máx. 100 caracteres  | Nombre (ej. "Happy Hour Jueves", "Fin de Semana") |
| `price`              | BigDecimal | Sí        | ≥ 0.00               | Precio aplicable                                  |
| `isBase`             | boolean    | No        | —                    | Si actúa como precio base                         |
| `discountPercentage` | BigDecimal | No        | 0.00 a 100.00        | Descuento opcional en %                           |
| `startDate`          | string     | No        | Formato `YYYY-MM-DD` | Fecha inicio de vigencia                          |
| `endDate`            | string     | No        | Formato `YYYY-MM-DD` | Fecha fin de vigencia                             |
| `startTime`          | string     | No        | Formato `HH:mm:ss`   | Hora de inicio                                    |
| `endTime`            | string     | No        | Formato `HH:mm:ss`   | Hora de fin                                       |
| `daysOfWeek`         | Short[]    | No        | Array de 1 a 7       | Días aplicables (1=Lunes, 7=Domingo)              |
| `priority`           | number     | No        | Integer              | Prioridad cuando hay coincidencia de horario      |
| `isActive`           | boolean    | No        | —                    | Estado activo                                     |

#### 📤 DTO: ProductResponse

| Campo          | Tipo                       | Nullable | Descripción                                           |
| -------------- | -------------------------- | -------- | ----------------------------------------------------- |
| `id`           | UUID                       | No       | Identificador único del producto                      |
| `tenantId`     | UUID                       | No       | Restaurante                                           |
| `categoryId`   | UUID                       | Sí       | Categoría                                             |
| `categoryName` | string                     | Sí       | Nombre de la categoría                                |
| `taxId`        | UUID                       | Sí       | ID del impuesto configurado                           |
| `taxName`      | string                     | Sí       | Nombre del impuesto (ej. "IGV 18%")                   |
| `taxRate`      | BigDecimal                 | Sí       | Tasa porcentual del impuesto                          |
| `taxInclusive` | boolean                    | Sí       | ¿El precio incluye el impuesto?                       |
| `name`         | string                     | No       | Nombre del producto                                   |
| `description`  | string                     | Sí       | Descripción detallada                                 |
| `price`        | BigDecimal                 | No       | Precio final vigente calculado para el momento actual |
| `basePrice`    | BigDecimal                 | No       | Precio base sin descuentos o sin impuestos extras     |
| `prices`       | List<ProductPriceResponse> | No       | Detalle de todas las reglas de precios configuradas   |
| `imageUrl`     | string                     | Sí       | URL de imagen                                         |
| `isAvailable`  | boolean                    | No       | Si se encuentra disponible para ordenar               |
| `createdAt`    | string                     | No       | ISO 8601                                              |
| `updatedAt`    | string                     | No       | ISO 8601                                              |

#### 📤 DTO: StorefrontProductResponse

| Campo                 | Tipo       | Nullable | Descripción                                                   |
| --------------------- | ---------- | -------- | ------------------------------------------------------------- |
| `id`                  | UUID       | No       | Identificador del producto                                    |
| `tenantId`            | UUID       | No       | Restaurante                                                   |
| `categoryId`          | UUID       | Sí       | Categoría                                                     |
| `categoryName`        | string     | Sí       | Nombre de la categoría                                        |
| `name`                | string     | No       | Nombre del producto                                           |
| `description`         | string     | Sí       | Descripción                                                   |
| `imageUrl`            | string     | Sí       | Imagen del producto                                           |
| `price`               | BigDecimal | No       | Precio de lista vigente calculado por `ProductPricingService` |
| `basePrice`           | BigDecimal | No       | Precio base regular del producto                              |
| `activePromotionName` | string     | Sí       | Nombre de la promoción vigente (null si aplica precio base)   |
| `discountPercentage`  | BigDecimal | Sí       | Porcentaje de descuento de la promoción vigente               |
| `taxId`               | UUID       | Sí       | ID del impuesto activo                                        |
| `taxName`             | string     | Sí       | Nombre del impuesto                                           |
| `taxRate`             | BigDecimal | No       | Tasa porcentual aplicada (0.00 si exento)                     |
| `taxInclusive`        | boolean    | Sí       | Si el precio ya incluye el impuesto                           |
| `baseUnitPrice`       | BigDecimal | No       | Valor unitario neto sin impuesto                              |
| `taxAmount`           | BigDecimal | No       | Monto unitario del impuesto                                   |
| `finalPrice`          | BigDecimal | No       | Precio final al comensal (`baseUnitPrice + taxAmount`)        |
| `isAvailable`         | boolean    | No       | Disponibilidad del producto                                   |

---

## 4. Cart — Carrito de Compras (`CartController`)

Cada cliente cuenta con un **único carrito persistente por restaurante** (`uk_carts_tenant_customer`: `tenantId, customerId`).
El cliente se resuelve automáticamente desde el token JWT y el `{slug}` de la tienda mediante `CurrentCustomerResolver` (creando el perfil `TenantCustomer` si aún no existe en la tienda, y rechazando con `403 Forbidden` si el cliente está bloqueado).

Ruta base: `/api/v1/store/{slug}/cart`  
**Autenticación:** Requerida (`Bearer <token>`)

---

### Endpoints de Carrito

| Método   | Ruta                                       | Descripción                                                                                                                   |
| -------- | ------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------- |
| `GET`    | `/api/v1/store/{slug}/cart`                | Obtiene el carrito del cliente en la tienda (recalcula precios dinámicos vigentes y verifica disponibilidad de cada producto) |
| `POST`   | `/api/v1/store/{slug}/cart/items`          | Agrega un producto al carrito (si el producto ya existe en el carrito, fusiona la cantidad y actualiza precio/notas)          |
| `PATCH`  | `/api/v1/store/{slug}/cart/items/{itemId}` | Actualiza la cantidad (`quantity`) y/o las notas (`notes`) de un ítem del carrito                                             |
| `DELETE` | `/api/v1/store/{slug}/cart/items/{itemId}` | Elimina un ítem específico del carrito                                                                                        |
| `PATCH`  | `/api/v1/store/{slug}/cart/notes`          | Actualiza las notas generales del carrito                                                                                     |
| `DELETE` | `/api/v1/store/{slug}/cart`                | Vacía todos los ítems del carrito del cliente en la tienda                                                                    |

---

### 📥 DTO: CartItemRequest

| Campo       | Tipo   | Requerido | Validación  | Descripción                                                                  |
| ----------- | ------ | --------- | ----------- | ---------------------------------------------------------------------------- |
| `productId` | UUID   | Sí        | UUID válido | Producto a agregar (debe pertenecer a la tienda `{slug}` y estar disponible) |
| `quantity`  | number | Sí        | ≥ 1         | Cantidad                                                                     |
| `notes`     | string | No        | —           | Observaciones / preferencias                                                 |

### 📥 DTO: CartItemUpdateRequest

| Campo      | Tipo   | Requerido | Validación     | Descripción                         |
| ---------- | ------ | --------- | -------------- | ----------------------------------- |
| `quantity` | number | No        | ≥ 1            | Nueva cantidad del ítem             |
| `notes`    | string | No        | Máx. 500 chars | Nuevas observaciones de preparación |

### 📥 DTO: CartNotesRequest

| Campo   | Tipo   | Requerido | Validación      | Descripción                 |
| ------- | ------ | --------- | --------------- | --------------------------- |
| `notes` | string | No        | Máx. 1000 chars | Notas generales del carrito |

### 📤 DTO: CartResponse

| Campo                 | Tipo                   | Nullable | Descripción                                                       |
| --------------------- | ---------------------- | -------- | ----------------------------------------------------------------- |
| `id`                  | UUID                   | No       | Identificador del carrito                                         |
| `tenantId`            | UUID                   | No       | ID del restaurante                                                |
| `tenantSlug`          | string                 | Sí       | Slug del restaurante                                              |
| `tenantName`          | string                 | Sí       | Nombre del restaurante                                            |
| `customerId`          | UUID                   | No       | ID del cliente en la tienda                                       |
| `customerUserId`      | UUID                   | No       | Usuario global del cliente                                        |
| `total`               | BigDecimal             | No       | Total acumulado de los ítems                                      |
| `notes`               | string                 | Sí       | Notas generales del carrito                                       |
| `items`               | List<CartItemResponse> | No       | Lista de productos agregados                                      |
| `hasPriceChanges`     | boolean                | No       | Indica si algún ítem cambió de precio respecto al guardado previo |
| `hasUnavailableItems` | boolean                | No       | Indica si algún producto del carrito dejó de estar disponible     |
| `warnings`            | List<String>           | No       | Mensajes informativos sobre cambios de precio o disponibilidad    |
| `createdAt`           | string                 | No       | ISO 8601                                                          |
| `updatedAt`           | string                 | No       | ISO 8601                                                          |

### 📤 DTO: CartItemResponse

| Campo               | Tipo       | Nullable | Descripción                                                  |
| ------------------- | ---------- | -------- | ------------------------------------------------------------ |
| `id`                | UUID       | No       | Identificador del ítem en carrito                            |
| `productId`         | UUID       | No       | Producto                                                     |
| `productName`       | string     | No       | Nombre del producto                                          |
| `productImageUrl`   | string     | Sí       | Imagen del producto                                          |
| `unitPrice`         | BigDecimal | No       | Precio unitario vigente                                      |
| `previousUnitPrice` | BigDecimal | Sí       | Precio unitario anterior (presente solo si hubo cambio)      |
| `priceChanged`      | boolean    | No       | Indica si el precio cambió al consultar el carrito           |
| `productAvailable`  | boolean    | No       | Indica si el producto sigue disponible y en categoría activa |
| `quantity`          | number     | No       | Cantidad seleccionada                                        |
| `subtotal`          | BigDecimal | No       | Subtotal (`quantity × unitPrice`)                            |
| `notes`             | string     | Sí       | Notas de preparación                                         |
| `createdAt`         | string     | Sí       | ISO 8601                                                     |
| `updatedAt`         | string     | Sí       | ISO 8601                                                     |

---

## 5. Order — Pedidos y Facturación (`StorefrontOrderController` y `OrderController`)

Módulo de órdenes de compra con checkout desde carrito, numeración secuencial por tienda (`ORD-000001`), control de estados y roles (`OrderStateMachine`), guardia de pagos (`PaymentGuard`), cobertura geográfica (`GeoUtils` + `deliveryRadiusKm`), registro fiscal inmutable y acumulación de puntos de fidelidad (`loyaltyPoints`).

---

### 🌐 Endpoints Storefront para Clientes (`StorefrontOrderController`)

Ruta base: `/api/v1/store/{slug}/orders`  
**Autenticación:** Requerida (`Bearer <token>`)

| Método | Ruta                                   | Descripción                                                                                                                                                                             |
| ------ | -------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `POST` | `/api/v1/store/{slug}/orders/checkout` | Inicia el checkout con Checkout Pro (Mercado Pago Orders API). Valida cobertura y stock, congela el `OrderDraft` y retorna `CheckoutPaymentResponse` con `checkoutUrl`.              |
| `POST` | `/api/v1/store/{slug}/orders/{id}/cancel` | Cancela el pedido del cliente y ejecuta el reembolso total en Mercado Pago. **Solo permitido en estado `PENDIENTE`**; en preparación o posterior retorna `409 Conflict`.          |
| `GET`  | `/api/v1/store/{slug}/orders`          | Lista paginada del historial de pedidos del cliente autenticado en la tienda `{slug}`.                                                                                                 |
| `GET`  | `/api/v1/store/{slug}/orders/{id}`     | Obtiene el detalle de un pedido propio del cliente autenticado en la tienda `{slug}` (incluye ETA dinámico si está en camino).                                                         |

#### Detalle de Checkout Storefront (`POST /api/v1/store/{slug}/orders/checkout`)

> Genera la orden de pago en Mercado Pago mediante la **Orders API v1** (`POST /v1/orders` en modo `online` y captura manual) vinculada a la cuenta del restaurante (100% de los fondos directos al tenant).  
>
> ⚠️ **Importante:** La orden (`Order`) en la base de datos **no se genera de inmediato** y el carrito **no se vacía** en esta llamada; se mantiene congelado un snapshot inmutable en `Payment.checkoutSnapshot`. La orden se materializa automáticamente y el carrito se vacía cuando el webhook de Mercado Pago confirma `APPROVED` (`status = processed`).

**Request Body:** `OrderCheckoutRequest`

##### 📤 Response (201 Created) — Envelope `ApiResponse<CheckoutPaymentResponse>`

```json
{
  "success": true,
  "message": "Orden de pago generada exitosamente en Mercado Pago",
  "data": {
    "paymentId": "7fa85f64-5717-4562-b3fc-2c963f66afa7",
    "mpOrderId": "ORD01HY78ZX90ABCD12345678",
    "checkoutUrl": "https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=12345678-abc-987",
    "status": "PENDING",
    "amount": 49.80,
    "currency": "PEN",
    "expiresAt": "2026-11-10T15:30:00Z"
  }
}
```

#### Detalle de Cancelación con Reembolso (`POST /api/v1/store/{slug}/orders/{id}/cancel`)

> Permite al cliente cancelar su orden y desencadenar un **reembolso total inmediato** en Mercado Pago (`POST /v1/orders/{mpOrderId}/refund`), marcando la orden como `CANCELADO` y liberando cualquier stock comprometido.

**Request Body (Opcional):** `OrderCancelRequest`

```json
{
  "reason": "Me equivoqué de dirección de entrega"
}
```

##### 📤 Response (200 OK) — Envelope `ApiResponse<OrderResponse>`

```json
{
  "success": true,
  "message": "Pedido cancelado y reembolsado exitosamente",
  "data": {
    "id": "1fa85f64-5717-4562-b3fc-2c963f66afa1",
    "orderNumber": "ORD-000142",
    "status": "CANCELADO",
    "total": 49.80,
    "notes": "Cancelado por el cliente. Motivo: Me equivoqué de dirección de entrega"
  }
}
```

### 🛡️ Endpoints Backoffice, KDS y Reparto (`OrderController`)

Ruta base: `/api/v1/tenants/{tenantId}/orders`  
**Autenticación:** Requerida (`Bearer <token>`)

| Método  | Ruta                                              | Rol Requerido                                                      | Descripción                                                                                                                          |
| ------- | ------------------------------------------------- | ------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------ |
| `GET`   | `/api/v1/tenants/{tenantId}/orders`               | Miembro (`OWNER`, `ADMIN`, `COCINERO`, `REPARTIDOR`, `SUPERADMIN`) | Listado paginado de órdenes del restaurante con filtros opcionales (`status`, `deliveryType`, `fromDate`, `toDate`)                  |
| `GET`   | `/api/v1/tenants/{tenantId}/orders/kds`           | `OWNER`, `ADMIN`, `COCINERO`, `SUPERADMIN`                         | Cola KDS (Kitchen Display System): órdenes en `EN_PREPARACION` ordenadas por antigüedad (`createdAt ASC`)                            |
| `GET`   | `/api/v1/tenants/{tenantId}/orders/my-deliveries` | `OWNER`, `ADMIN`, `REPARTIDOR`, `SUPERADMIN`                       | Órdenes asignadas al repartidor autenticado (filtrable por `status`)                                                                 |
| `GET`   | `/api/v1/tenants/{tenantId}/orders/{id}`          | Miembro (`OWNER`, `ADMIN`, `COCINERO`, `REPARTIDOR`, `SUPERADMIN`) | Obtiene el detalle completo de una orden del restaurante                                                                             |
| `PATCH` | `/api/v1/tenants/{tenantId}/orders/{id}/status`   | Miembro (validado por `OrderStateMachine` según rol)               | Actualiza el estado del pedido (`COCINERO`: preparación/listo; `REPARTIDOR`: en camino/entregado; `OWNER`/`ADMIN`: todos + cancelar) |
| `PATCH` | `/api/v1/tenants/{tenantId}/orders/{id}/assign`   | `OWNER`, `ADMIN`, `SUPERADMIN`                                     | Asigna un miembro activo con rol `REPARTIDOR` a un pedido `DELIVERY`                                                                 |

---

### 📥 DTO: OrderCheckoutRequest

| Campo               | Tipo   | Requerido | Validación                        | Descripción                                          |
| ------------------- | ------ | --------- | --------------------------------- | ---------------------------------------------------- |
| `deliveryType`      | string | Sí        | `DELIVERY`, `TAKEAWAY`, `DINE_IN` | Modalidad de entrega                                 |
| `customerAddressId` | UUID   | No        | UUID válido (propiedad del user)  | Dirección guardada seleccionada                      |
| `deliveryAddress`   | string | No        | —                                 | Dirección de entrega en texto                        |
| `deliveryLatitude`  | number | No        | Double (WGS84)                    | Latitud de entrega                                   |
| `deliveryLongitude` | number | No        | Double (WGS84)                    | Longitud de entrega                                  |
| `ruc`               | string | No        | Máx. 20 chars                     | RUC opcional para facturación                        |
| `notes`             | string | No        | Máx. 1000 chars                   | Notas del pedido (si se omite, toma las del carrito) |

### 📥 DTO: OrderStatusUpdateRequest

| Campo    | Tipo   | Requerido | Validación    | Descripción              |
| -------- | ------ | --------- | ------------- | ------------------------ |
| `status` | string | Sí        | `OrderStatus` | Nuevo estado de la orden |

### 📥 DTO: OrderCancelRequest

| Campo    | Tipo   | Requerido | Validación           | Descripción                            |
| -------- | ------ | --------- | -------------------- | -------------------------------------- |
| `reason` | string | No        | Máx. 500 caracteres  | Motivo de la cancelación por el cliente|

### 📥 DTO: OrderAssignStaffRequest

| Campo             | Tipo | Requerido | Validación  | Descripción                                      |
| ----------------- | ---- | --------- | ----------- | ------------------------------------------------ |
| `deliveryStaffId` | UUID | Sí        | UUID válido | `userId` del miembro activo con rol `REPARTIDOR` |

---

### 📥 DTO: OrderCreateRequest

| Campo               | Tipo                   | Requerido | Validación                        | Descripción                            |
| ------------------- | ---------------------- | --------- | --------------------------------- | -------------------------------------- |
| `tenantId`          | UUID                   | Sí        | UUID válido                       | Restaurante donde se realiza el pedido |
| `deliveryType`      | string                 | Sí        | `DELIVERY`, `TAKEAWAY`, `DINE_IN` | Modalidad de entrega                   |
| `customerAddressId` | UUID                   | No        | UUID válido                       | Dirección guardada seleccionada        |
| `deliveryAddress`   | string                 | No        | —                                 | Texto de la dirección de entrega       |
| `deliveryLatitude`  | number                 | No        | Double (WGS84)                    | Latitud de entrega                     |
| `deliveryLongitude` | number                 | No        | Double (WGS84)                    | Longitud de entrega                    |
| `notes`             | string                 | No        | —                                 | Indicaciones de preparación o entrega  |
| `items`             | List<OrderItemRequest> | Sí        | Mínimo 1 ítem                     | Productos solicitados                  |

#### 📥 DTO: OrderItemRequest

| Campo       | Tipo   | Requerido | Validación  | Descripción                    |
| ----------- | ------ | --------- | ----------- | ------------------------------ |
| `productId` | UUID   | Sí        | UUID válido | Producto a comprar             |
| `quantity`  | number | Sí        | ≥ 1         | Cantidad                       |
| `notes`     | string | No        | —           | Observaciones para el cocinero |

---

### 📤 DTO: OrderResponse

| Campo                      | Tipo                    | Nullable | Descripción                                                                |
| -------------------------- | ----------------------- | -------- | -------------------------------------------------------------------------- |
| `id`                       | UUID                    | No       | Identificador único del pedido                                             |
| `tenantId`                 | UUID                    | No       | Restaurante asociado                                                       |
| `tenantSlug`               | string                  | No       | Slug identificador de la tienda                                            |
| `tenantName`               | string                  | No       | Nombre comercial del restaurante                                           |
| `customerId`               | UUID                    | No       | ID de cliente en tienda                                                    |
| `customerUserId`           | UUID                    | No       | ID de usuario global                                                       |
| `customerFullName`         | string                  | Sí       | Nombre del cliente                                                         |
| `customerEmail`            | string                  | Sí       | Email del cliente                                                          |
| `customerPhone`            | string                  | Sí       | Teléfono de contacto                                                       |
| `deliveryStaffId`          | UUID                    | Sí       | ID del repartidor asignado                                                 |
| `deliveryStaffName`        | string                  | Sí       | Nombre completo del repartidor asignado                                    |
| `orderNumber`              | string                  | No       | Código legible del pedido (ej. "ORD-000123")                               |
| `ruc`                      | string                  | Sí       | RUC para facturación electrónica                                           |
| `deliveryType`             | string                  | No       | `DELIVERY`, `TAKEAWAY`, `DINE_IN`                                          |
| `status`                   | string                  | No       | `PENDIENTE`, `EN_PREPARACION`, `LISTO_PARA_ENTREGA`, `EN_CAMINO`, `ENTREGADO`, `CANCELADO` |
| `deliveryAddress`          | string                  | Sí       | Dirección histórica inmutable                                              |
| `deliveryLatitude`         | number                  | Sí       | Latitud de entrega                                                         |
| `deliveryLongitude`        | number                  | Sí       | Longitud de entrega                                                        |
| `estimatedDistanceKm`      | BigDecimal              | Sí       | Distancia calculada por PostGIS entre restaurante y cliente                |
| `estimatedDeliveryMinutes` | number                  | Sí       | Tiempo estimado de entrega en minutos (recalculado en tiempo real con GPS) |
| `estimatedArrivalAt`       | string                  | Sí       | Hora estimada de llegada del repartidor (ISO 8601)                         |
| `etaMessage`               | string                  | Sí       | Mensaje formateado para el comensal (ej. "Tu pedido llegará en ~12 min")  |
| `dispatchedAt`             | string                  | Sí       | Momento en que el repartidor inició ruta `EN_CAMINO` (ISO 8601)            |
| `deliveredAt`              | string                  | Sí       | Momento en que la orden pasó a `ENTREGADO` (ISO 8601)                      |
| `subtotal`                 | BigDecimal              | No       | Suma de valores netos imponibles                                           |
| `taxTotal`                 | BigDecimal              | No       | Total de impuestos calculados (ej. IGV acumulado)                          |
| `deliveryFee`              | BigDecimal              | No       | Costo de envío aplicado                                                    |
| `total`                    | BigDecimal              | No       | Monto total final a pagar                                                  |
| `notes`                    | string                  | Sí       | Notas del pedido                                                           |
| `items`                    | List<OrderItemResponse> | No       | Detalle de los platillos del pedido                                        |
| `createdAt`                | string                  | No       | Fecha de creación (ISO 8601)                                               |
| `updatedAt`                | string                  | No       | Fecha de actualización (ISO 8601)                                          |

---

### 📤 DTO: OrderItemResponse (Snapshot Fiscal Inmutable)

| Campo         | Tipo       | Nullable | Descripción                                                          |
| ------------- | ---------- | -------- | -------------------------------------------------------------------- |
| `id`          | UUID       | No       | Identificador del ítem                                               |
| `productId`   | UUID       | Sí       | ID del producto (conserva null si se elimina del catálogo posterior) |
| `productName` | string     | No       | **Snapshot inmutable** del nombre del producto                       |
| `taxId`       | UUID       | Sí       | Impuesto aplicado al momento de ordenar                              |
| `taxRate`     | BigDecimal | No       | **Snapshot inmutable** de la tasa porcentual (ej. 18.00)             |
| `taxAmount`   | BigDecimal | No       | **Snapshot inmutable** del monto de impuesto calculado               |
| `unitPrice`   | BigDecimal | No       | **Snapshot inmutable** del precio unitario cobrado                   |
| `quantity`    | number     | No       | Cantidad solicitada                                                  |
| `subtotal`    | BigDecimal | No       | Subtotal (quantity × unitPrice)                                      |
| `notes`       | string     | Sí       | Instrucciones del ítem                                               |
| `createdAt`   | string     | No       | ISO 8601                                                             |
| `updatedAt`   | string     | No       | ISO 8601                                                             |

#### Ciclo de Vida del Pedido (`OrderStatus`):

```
PENDIENTE  ──>  EN_PREPARACION  ──>  LISTO_PARA_ENTREGA  ──>  EN_CAMINO  ──>  ENTREGADO
    │                 │                      │                     │
    └─────────────────┴────────── CANCELADO ─┴─────────────────────┘
```

> **🛡️ Regla Anti-Fraude:** El pedido solo puede pasar de `PENDIENTE` a `EN_PREPARACION` tras verificar que el pago asociado tiene el estado `APPROVED`.

---

## 6. Subscription — Suscripciones SaaS para Restaurantes

Módulo de monetización SaaS que administra los planes comerciales de la plataforma y el ciclo de vida de las suscripciones de los restaurantes (TRIAL, ACTIVE, PAST_DUE, CANCELED, EXPIRED).

---

### 💳 Planes de Suscripción Públicos (SubscriptionPlanController)

Ruta base: /api/v1/plans

---

#### GET /api/v1/plans

> Obtiene la lista de planes de suscripción activos disponibles públicamente en la plataforma.

**Autenticación:** No requerida (Público)  
**Rol requerido:** Ninguno

##### 📤 Response

**Éxito (200 OK):**
`json
{
  "success": true,
  "message": "Planes de suscripción obtenidos exitosamente",
  "data": [
    {
      "id": "p1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "name": "Plan Emprendedor",
      "price": 59.90,
      "billingCycleDays": 30,
      "maxProducts": 50,
      "isActive": true,
      "createdAt": "2026-10-22T00:00:00Z",
      "updatedAt": "2026-10-22T00:00:00Z"
    }
  ]
}
`

---

### 🛡️ Administración de Planes (AdminSubscriptionPlanController - SUPERADMIN)

Ruta base: /api/v1/admin/plans

---

#### GET /api/v1/admin/plans

> Lista todos los planes registrados en la plataforma, incluyendo inactivos.

**Autenticación:** Requerida (Bearer <token>)  
**Rol requerido:** SUPERADMIN

##### 📤 Response

**Éxito (200 OK):**
`json
{
  "success": true,
  "message": "Todos los planes obtenidos exitosamente",
  "data": [
    {
      "id": "p1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "name": "Plan Emprendedor",
      "price": 59.90,
      "billingCycleDays": 30,
      "maxProducts": 50,
      "isActive": true
    }
  ]
}
`

---

#### GET /api/v1/admin/plans/{planId}

> Obtiene los datos de un plan por su ID único.

**Autenticación:** Requerida (Bearer <token>)  
**Rol requerido:** SUPERADMIN

##### 📤 Response

**Éxito (200 OK):** Retorna ApiResponse<SubscriptionPlanResponse>.

---

#### POST /api/v1/admin/plans

> Crea un nuevo plan de suscripción en la plataforma.

**Autenticación:** Requerida (Bearer <token>)  
**Rol requerido:** SUPERADMIN  
**Content-Type:** pplication/json

##### 📥 Request

**Body (JSON) — SubscriptionPlanRequest:**

| Campo | Tipo | Requerido | Validación | Descripción |
| ----- | ---- | --------- | ---------- | ----------- |

|
ame | string | Sí | Máx. 100 caracteres, único | Nombre comercial del plan |
| price | BigDecimal | Sí | ≥ 0.00 | Tarifa del plan |
| illingCycleDays | number | Sí | ≥ 1 (default: 30) | Días de vigencia por ciclo |
| maxProducts | number | No | ≥ 1 | Límite máximo de productos permitidos |
| isActive | boolean | No | Default: rue | Si el plan está visible para contratación |

`json
{
  "name": "Plan Pro",
  "price": 99.90,
  "billingCycleDays": 30,
  "maxProducts": 150,
  "isActive": true
}
`

##### 📤 Response

**Éxito (201 Created):**
`json
{
  "success": true,
  "message": "Plan de suscripción creado exitosamente",
  "data": {
    "id": "p2a2c3d4-e5f6-7890-abcd-ef1234567891",
    "name": "Plan Pro",
    "price": 99.90,
    "billingCycleDays": 30,
    "maxProducts": 150,
    "isActive": true,
    "createdAt": "2026-10-22T02:00:00Z",
    "updatedAt": "2026-10-22T02:00:00Z"
  }
}
`

---

#### PUT /api/v1/admin/plans/{planId}

> Actualiza la información y límites de un plan existente.

**Autenticación:** Requerida (Bearer <token>)  
**Rol requerido:** SUPERADMIN  
**Content-Type:** pplication/json

##### 📥 Request

Mismo formato SubscriptionPlanRequest.

##### 📤 Response

**Éxito (200 OK):** Retorna ApiResponse<SubscriptionPlanResponse>.

---

#### PATCH /api/v1/admin/plans/{planId}/status

> Activa o desactiva la disponibilidad de un plan.

**Autenticación:** Requerida (Bearer <token>)  
**Rol requerido:** SUPERADMIN  
**Content-Type:** pplication/json

##### 📥 Request

**Body (JSON) — SubscriptionPlanStatusRequest:**

| Campo    | Tipo    | Requerido | Descripción                            |
| -------- | ------- | --------- | -------------------------------------- |
| isActive | boolean | Sí        | rue para activar, alse para desactivar |

`json
{
  "isActive": false
}
`

##### 📤 Response

**Éxito (200 OK):** Retorna ApiResponse<SubscriptionPlanResponse>.

---

### 📦 Suscripciones del Usuario (SubscriptionController)

Ruta base: /api/v1/subscriptions

---

#### POST /api/v1/subscriptions

> Inicia una nueva suscripción en período de prueba (TRIAL) para el usuario autenticado. El usuario se obtiene del token JWT.

**Autenticación:** Requerida (Bearer <token>)  
**Rol requerido:** Cualquier usuario autenticado sin suscripción vigente  
**Content-Type:** pplication/json

##### 📥 Request

**Body (JSON) — SubscriptionCreateRequest:**

| Campo  | Tipo | Requerido | Descripción         |
| ------ | ---- | --------- | ------------------- |
| planId | UUID | Sí        | ID del plan elegido |

`json
{
  "planId": "p1a2c3d4-e5f6-7890-abcd-ef1234567890"
}
`

##### 📤 Response

**Éxito (201 Created):**
`json
{
  "success": true,
  "message": "Suscripción iniciada exitosamente",
  "data": {
    "id": "s1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "plan": {
      "id": "p1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "name": "Plan Emprendedor",
      "price": 59.90,
      "billingCycleDays": 30,
      "maxProducts": 50,
      "isActive": true
    },
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "status": "TRIAL",
    "startDate": "2026-10-22T02:00:00Z",
    "endDate": "2026-11-21T02:00:00Z",
    "mpPreapprovalId": null,
    "createdAt": "2026-10-22T02:00:00Z",
    "updatedAt": "2026-10-22T02:00:00Z"
  }
}
`

---

#### GET /api/v1/subscriptions/me

> Obtiene la suscripción vigente o más reciente del usuario autenticado actual.

**Autenticación:** Requerida (Bearer <token>)  
**Rol requerido:** Cualquier usuario autenticado

##### 📤 Response

**Éxito (200 OK):**
`json
{
  "success": true,
  "message": "Suscripción obtenida exitosamente",
  "data": {
    "id": "s1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "plan": {
      "id": "p1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "name": "Plan Emprendedor",
      "maxProducts": 50
    },
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "status": "TRIAL",
    "startDate": "2026-10-22T02:00:00Z",
    "endDate": "2026-11-21T02:00:00Z"
  }
}
`

---

#### POST /api/v1/subscriptions/checkout

> Genera una suscripción recurrente con **pagos preaprobados en Mercado Pago** (`POST /preapproval`). Crea la suscripción en base de datos con estado `PENDING` y devuelve la URL `initPoint` para que el usuario autorice el débito automático periódico en Mercado Pago.
>
> Cuando Mercado Pago procesa el cobro y envía el webhook correspondiente, la suscripción pasa a `ACTIVE` y se registra el cobro en el historial.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquier usuario autenticado  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON) — `SubscriptionCreateRequest`:**

| Campo    | Tipo | Requerido | Descripción         |
| -------- | ---- | --------- | ------------------- |
| `planId` | UUID | Sí        | ID del plan elegido |

```json
{
  "planId": "p1a2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

##### 📤 Response

**Éxito (201 Created):**

```json
{
  "success": true,
  "message": "Enlace de suscripción preaprobada generado exitosamente",
  "data": {
    "subscriptionId": "s1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "planId": "p1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "mpPreapprovalId": "2c9380847290abcd017291234567890a",
    "initPoint": "https://www.mercadopago.com.pe/subscriptions/checkout?preapproval_id=2c9380847290abcd017291234567890a",
    "status": "PENDING"
  }
}
```

---

#### GET /api/v1/subscriptions/me/payments

> Lista el historial de cobros recurrentes de la suscripción del usuario autenticado (con ID de transacción en Mercado Pago, montos, fecha de cobro y estado).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Propietario de la suscripción

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Historial de cobros de suscripción obtenido exitosamente",
  "data": [
    {
      "id": "b1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "subscriptionId": "s1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "mpAuthorizedPaymentId": "98765432101",
      "mpPaymentId": "12345678901",
      "status": "APPROVED",
      "amount": 59.90,
      "currency": "PEN",
      "paidAt": "2026-11-01T10:00:00Z",
      "periodEnd": "2026-12-01T10:00:00Z",
      "createdAt": "2026-11-01T10:00:05Z"
    }
  ]
}
```

---

#### PATCH /api/v1/subscriptions/me/cancel

> Cancela la suscripción vigente del usuario autenticado.  
>
> ⚠️ **Comportamiento según el tipo de suscripción:**
> - **Suscripción de prueba (TRIAL) o manual:** Pasa de inmediato a `CANCELED` y desactiva el restaurante vinculado.
> - **Suscripción pagada recurrente con Mercado Pago (`mpPreapprovalId`):** Cancela la recurrencia automática en Mercado Pago (`status: cancelled`) y marca `cancelAtPeriodEnd = true`. **Mantiene el acceso al restaurante abierto hasta el término del período ya facturado (`endDate`)**; llegada esa fecha, un scheduler la expira automáticamente.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Usuario autenticado con suscripción activa o trial

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Suscripción cancelada exitosamente",
  "data": {
    "id": "s1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "status": "ACTIVE",
    "cancelAtPeriodEnd": true,
    "endDate": "2026-12-01T10:00:00Z",
    "mpPreapprovalStatus": "cancelled"
  }
}
```

---

### 📦 Modelos de Datos / DTOs de Suscripción

#### 📥 DTO: SubscriptionPlanRequest

| Campo              | Tipo       | Requerido | Validación    | Descripción                                 |
| ------------------ | ---------- | --------- | ------------- | ------------------------------------------- |
| `name`             | string     | Sí        | Máx. 100 char | Nombre del plan (ej. "Plan Emprendedor")    |
| `price`            | BigDecimal | Sí        | ≥ 0.00        | Tarifa recurrente del plan                  |
| `billingCycleDays` | number     | Sí        | ≥ 1           | Días del ciclo de facturación (default: 30) |
| `maxProducts`      | number     | No        | ≥ 1           | Límite de productos en el catálogo          |
| `isActive`         | boolean    | No        | —             | Estado activo                               |

#### 📤 DTO: SubscriptionPlanResponse

| Campo              | Tipo       | Nullable | Descripción                   |
| ------------------ | ---------- | -------- | ----------------------------- |
| `id`               | UUID       | No       | ID del plan                   |
| `name`             | string     | No       | Nombre del plan               |
| `price`            | BigDecimal | No       | Precio recurrente             |
| `billingCycleDays` | number     | No       | Días del período              |
| `maxProducts`      | number     | Sí       | Capacidad máxima de productos |
| `isActive`         | boolean    | No       | Estado del plan               |

#### 📥 DTO: SubscriptionCreateRequest

| Campo    | Tipo | Requerido | Descripción         |
| -------- | ---- | --------- | ------------------- |
| `planId` | UUID | Sí        | ID del plan elegido |

#### 📤 DTO: SubscriptionCheckoutResponse

| Campo             | Tipo               | Nullable | Descripción                                               |
| ----------------- | ------------------ | -------- | --------------------------------------------------------- |
| `subscriptionId`  | UUID               | No       | Identificador de la suscripción generada                  |
| `planId`          | UUID               | No       | ID del plan SaaS contratado                               |
| `mpPreapprovalId` | string             | No       | Identificador de suscripción recurrente en Mercado Pago   |
| `initPoint`       | string             | No       | URL de checkout de Mercado Pago para autorizar la tarjeta |
| `status`          | SubscriptionStatus | No       | `PENDING`, `ACTIVE`, etc.                                 |

#### 📤 DTO: SubscriptionPaymentResponse

| Campo                   | Tipo                      | Nullable | Descripción                                       |
| ----------------------- | ------------------------- | -------- | ------------------------------------------------- |
| `id`                    | UUID                      | No       | Identificador del cobro                           |
| `subscriptionId`        | UUID                      | No       | ID de la suscripción vinculada                    |
| `mpAuthorizedPaymentId` | string                    | Sí       | ID del pago autorizado recurrente en Mercado Pago |
| `mpPaymentId`           | string                    | Sí       | ID del pago procesado                             |
| `status`                | SubscriptionPaymentStatus | No       | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`    |
| `amount`                | BigDecimal                | No       | Monto debitado                                    |
| `currency`              | string                    | No       | Moneda (`PEN`)                                    |
| `paidAt`                | string                    | Sí       | Fecha de confirmación del cobro (ISO 8601)        |
| `periodEnd`             | string                    | Sí       | Fin de cobertura del período facturado (ISO 8601) |
| `createdAt`             | string                    | No       | Fecha de registro (ISO 8601)                      |

#### 📤 DTO: SubscriptionResponse

| Campo                 | Tipo                     | Nullable | Descripción                                                                |
| --------------------- | ------------------------ | -------- | -------------------------------------------------------------------------- |
| `id`                  | UUID                     | No       | ID de la suscripción                                                       |
| `plan`                | SubscriptionPlanResponse | No       | Detalle del plan contratado                                                |
| `userId`              | UUID                     | No       | Propietario de la cuenta                                                   |
| `status`              | string                   | No       | `TRIAL`, `ACTIVE`, `PENDING`, `PAST_DUE`, `CANCELED`, `EXPIRED`            |
| `startDate`           | string                   | No       | Fecha inicio (ISO 8601)                                                    |
| `endDate`             | string                   | No       | Fecha fin del período (ISO 8601)                                           |
| `mpPreapprovalId`     | string                   | Sí       | ID de suscripción automática Mercado Pago                                  |
| `mpPreapprovalStatus` | string                   | Sí       | Estado de la recurrencia en MP (`authorized`, `cancelled`)                 |
| `cancelAtPeriodEnd`   | boolean                  | Sí       | Indica si la suscripción se cancelará al llegar al final del período actual|

---

## 7. Payment — Pagos 100% Mercado Pago (Orders API Checkout Pro, OAuth, Preapproval y Webhooks)

> **Arquitectura Financiera:** Integración directa con **Mercado Pago** que garantiza cobro seguro y conciliación automática:
> 1. **Cobro Directo al Restaurante (OAuth Multi-Tenant):** Cada restaurante vincula su propia cuenta de Mercado Pago mediante OAuth 2.0. El 100% de los ingresos de sus pedidos en línea va directamente a su cuenta, sin intermediación de fondos por la plataforma.
> 2. **SaaS Recurrente (Preapproval):** El cobro mensual del plan SaaS del restaurante se gestiona mediante suscripciones preaprobadas (`POST /preapproval`) cargadas a la cuenta del usuario.
> 3. **Checkout Pro vía Orders API v1 (`POST /v1/orders`):** Proporciona la experiencia moderna de Checkout Pro (tarjetas de crédito/débito, Yape, PagoEfectivo, saldo en cuenta). En el checkout se congela el snapshot del carrito (`OrderDraft`) sin crear aún la orden en BD.
> 4. **Webhooks con Firma Criptográfica (`x-signature`):** Validación HMAC-SHA256 del manifiesto de notificación (`id;request-id;ts`). Al confirmarse el pago (`status = processed` / `APPROVED`), se materializa la orden (`Order` en `PENDIENTE`), se descuenta el stock de recetas y se limpia el carrito.
> 5. **Reembolsos Automáticos:** Cancelación de pedidos en estado `PENDIENTE` con devolución inmediata y total en Mercado Pago (`POST /v1/orders/{mpOrderId}/refund`).

---

### 🔗 7.1 Conexión OAuth del Restaurante (`MercadoPagoConnectionController` y `MercadoPagoOAuthCallbackController`)

Ruta base: `/api/v1/tenants/{tenantId}/mercadopago` y `/api/v1/mercadopago/oauth`

---

#### GET `/api/v1/tenants/{tenantId}/mercadopago/connect`

> Genera la URL oficial de autorización OAuth de Mercado Pago para que el dueño del restaurante vincule su cuenta. El enlace incluye un parámetro `state` firmado criptográficamente con HMAC que vincula el `tenantId` y expira en 10 minutos para evitar ataques CSRF.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` del restaurante

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "URL de autorización de Mercado Pago generada exitosamente",
  "data": {
    "authorizationUrl": "https://auth.mercadopago.com.pe/authorization?client_id=1234567890&response_type=code&platform_id=mp&state=eyJhbGciOiJIUzI1NiJ9...&redirect_uri=https%3A%2F%2Fapi.nexofood.lat%2Fapi%2Fv1%2Fmercadopago%2Foauth%2Fcallback",
    "expiresInSeconds": 600
  }
}
```

---

#### GET `/api/v1/tenants/{tenantId}/mercadopago/status`

> Consulta el estado de conexión de Mercado Pago del restaurante. Informa si la tienda tiene credenciales válidas y la fecha de expiración del token OAuth.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Estado de conexión de Mercado Pago obtenido exitosamente",
  "data": {
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "connected": true,
    "mpUserId": "123456789",
    "connectedAt": "2026-11-09T22:15:00Z",
    "tokenExpiresAt": "2027-05-09T22:15:00Z"
  }
}
```

---

#### DELETE `/api/v1/tenants/{tenantId}/mercadopago`

> Desvincula la cuenta de Mercado Pago del restaurante. Borra y revoca de forma segura los tokens OAuth cifrados (AES-256-GCM) almacenados para ese restaurante.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` del restaurante

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Cuenta de Mercado Pago desvinculada exitosamente"
}
```

---

#### GET `/api/v1/mercadopago/oauth/callback`

> Endpoint público de redirección registrado en el panel de desarrolladores de Mercado Pago. Valida el parámetro `state`, intercambia el código de autorización temporal (`code`) por el `access_token` y `refresh_token`, cifra las credenciales con AES-256-GCM y actualiza el restaurante.
>
> - **Navegadores (`Accept: text/html`):** Responde con redirección `302 Found` al frontend del panel del restaurante (`/admin/settings/payments?mp=connected`).
> - **Clientes API (JSON):** Responde `200 OK` con envelope `ApiResponse`.

**Autenticación:** No requerida (Público)  
**Query Parameters:**
- `code` (string): Código de autorización provisto por Mercado Pago.
- `state` (string): Token firmado con HMAC-SHA256 emitido previamente por `/connect`.
- `error` (string, opcional): Código de error en caso de rechazo del usuario.

##### 📤 Response (Clientes API / Postman / Bruno — 200 OK)

```json
{
  "success": true,
  "message": "Cuenta de Mercado Pago vinculada exitosamente",
  "data": {
    "connected": true,
    "redirectUrl": "http://localhost:3000/admin/settings/payments?mp=connected"
  }
}
```

---

### 💳 7.2 Storefront — Consulta de Estado del Pago (`StorefrontPaymentController`)

Ruta base: `/api/v1/store/{slug}/payments`

---

#### GET `/api/v1/store/{slug}/payments/{paymentId}`

> Permite al comensal consultar el estado de su pago tras regresar de la pasarela de Checkout Pro (`checkoutUrl`). Cuando el webhook de Mercado Pago procesa la transacción, este endpoint entrega el estado final (`APPROVED`), el `orderId` y el `orderNumber` del pedido generado.

**Autenticación:** Requerida (`Bearer <token>` del cliente)  
**Rol requerido:** Cliente propietario del pago

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Estado del pago obtenido exitosamente",
  "data": {
    "paymentId": "7fa85f64-5717-4562-b3fc-2c963f66afa7",
    "status": "APPROVED",
    "statusDetail": "accredited",
    "orderId": "1fa85f64-5717-4562-b3fc-2c963f66afa1",
    "orderNumber": "ORD-000142",
    "amount": 49.80,
    "currency": "PEN",
    "approvedAt": "2026-11-10T14:45:00Z",
    "refundedAt": null
  }
}
```

---

### 🛡️ 7.3 Backoffice — Auditoría de Pagos del Restaurante (`TenantPaymentController`)

Ruta base: `/api/v1/tenants/{tenantId}`

---

#### GET `/api/v1/tenants/{tenantId}/payments`

> Lista de forma paginada todos los intentos y transacciones de pago recibidas en el restaurante con opción de filtrar por estado (`status`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)  
**Query Parameters:**
- `status` (string, opcional): Filtra por `PaymentStatus` (`PENDING`, `IN_PROCESS`, `APPROVED`, `REJECTED`, `CANCELLED`, `EXPIRED`, `REFUNDED`).
- `page` (number, default: 0), `size` (number, default: 20), `sort` (default: `createdAt,desc`).

##### 📤 Response

**Éxito (200 OK):** Retorna `ApiResponse<PageResponse<PaymentResponse>>`.

---

#### GET `/api/v1/tenants/{tenantId}/orders/{orderId}/payment`

> Obtiene el registro de pago y detalle de pasarela Mercado Pago correspondiente a una orden específica del restaurante.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante (`OWNER`, `ADMIN`, `COCINERO`, `REPARTIDOR`)

##### 📤 Response

**Éxito (200 OK):** Retorna `ApiResponse<PaymentResponse>`.

---

### 🔔 7.4 Webhook de Mercado Pago (`MercadoPagoWebhookController`)

Ruta base: `/api/v1/webhooks/mercadopago`

---

#### POST `/api/v1/webhooks/mercadopago`

> Recibe las notificaciones asíncronas de eventos desde los servidores de Mercado Pago (`order`, `payment`, `subscription_preapproval`, `subscription_authorized_payment`).
>
> 🔐 **Seguridad HMAC-SHA256 Obligatoria:**
> - Requiere la cabecera `x-signature` que contiene `ts` y el hash `v1`.
> - Valida que el manifiesto `id:{data.id};request-id:{x-request-id};ts:{ts};` coincida exactamente con el hash HMAC-SHA256 usando el secreto configurado en `MP_WEBHOOK_SECRET`. Si la firma no coincide o es inválida, responde `401 Unauthorized`.
>
> ⚙️ **Procesamiento de Órdenes Online:**
> - Al recibir un webhook de orden, consulta a la API de Mercado Pago (`GET /v1/orders/{id}`).
> - Cuando la orden alcanza `status = processed` (`APPROVED`), crea la orden (`Order`) a partir del borrador congelado en `Payment.checkoutSnapshot`, genera el correlativo `ORD-XXXXXX`, descuenta los insumos de receta en el inventario, acumula puntos de lealtad y vacía el carrito del comensal.

**Autenticación:** Firma criptográfica en cabeceras (`x-signature`, `x-request-id`)  
**Content-Type:** `application/json`

##### 📥 Request Headers

```http
x-signature: ts=1731248400,v1=a1b2c3d4e5f67890abcdef1234567890abcdef1234567890abcdef1234567890
x-request-id: 3fa85f64-5717-4562-b3fc-2c963f66afa6
```

##### 📥 Request Body (JSON)

```json
{
  "action": "order.processed",
  "api_version": "v1",
  "data": {
    "id": "ORD01HY78ZX90ABCD12345678"
  },
  "date_created": "2026-11-10T14:45:00Z",
  "id": 1234567890,
  "live_mode": false,
  "type": "order",
  "user_id": 987654321
}
```

##### 📤 Response

**Éxito (200 OK):** HTTP Status `200 OK` vacío.

---

### 📦 Modelos de Datos / DTOs de Payment

#### 📤 DTO: CheckoutPaymentResponse

| Campo         | Tipo          | Nullable | Descripción                                               |
| ------------- | ------------- | -------- | --------------------------------------------------------- |
| `paymentId`   | UUID          | No       | Identificador del intento de pago en NexoFood             |
| `mpOrderId`   | string        | No       | Identificador de la orden en Mercado Pago (`ORD...`)     |
| `checkoutUrl` | string        | No       | URL de Checkout Pro para redirigir al cliente             |
| `status`      | PaymentStatus | No       | `PENDING`, `APPROVED`, etc.                               |
| `amount`      | BigDecimal    | No       | Importe a pagar                                           |
| `currency`    | string        | No       | Moneda (`PEN`)                                            |
| `expiresAt`   | string        | No       | Fecha límite para completar el pago (ISO 8601)            |

#### 📤 DTO: MercadoPagoConnectResponse

| Campo              | Tipo   | Nullable | Descripción                                              |
| ------------------ | ------ | -------- | -------------------------------------------------------- |
| `authorizationUrl` | string | No       | Enlace oficial de autorización OAuth de Mercado Pago     |
| `expiresInSeconds` | number | No       | Tiempo de validez del enlace firmado (600 segundos = 10m)|

#### 📤 DTO: MercadoPagoStatusResponse

| Campo             | Tipo    | Nullable | Descripción                                       |
| ----------------- | ------- | -------- | ------------------------------------------------- |
| `tenantId`        | UUID    | No       | Restaurante consultado                            |
| `connected`       | boolean | No       | `true` si tiene credenciales activas vinculadas   |
| `mpUserId`        | string  | Sí       | User ID de la cuenta en Mercado Pago              |
| `connectedAt`     | string  | Sí       | Fecha en que se vinculó la cuenta (ISO 8601)      |
| `tokenExpiresAt`  | string  | Sí       | Fecha de expiración del token OAuth (ISO 8601)    |

#### 📤 DTO: PaymentStatusResponse

| Campo          | Tipo          | Nullable | Descripción                                               |
| -------------- | ------------- | -------- | --------------------------------------------------------- |
| `paymentId`    | UUID          | No       | Identificador único del pago en NexoFood                  |
| `status`       | PaymentStatus | No       | `PENDING`, `IN_PROCESS`, `APPROVED`, `REJECTED`, etc.     |
| `statusDetail` | string        | Sí       | Detalle devuelto por Mercado Pago (ej. "accredited")      |
| `orderId`      | UUID          | Sí       | ID del pedido generado una vez aprobado el pago           |
| `orderNumber`  | string        | Sí       | Código correlativo del pedido (ej. "ORD-000142")          |
| `amount`       | BigDecimal    | No       | Monto total cobrado                                       |
| `currency`     | string        | No       | Moneda (`PEN`)                                            |
| `approvedAt`   | string        | Sí       | Fecha y hora de aprobación (ISO 8601)                     |
| `refundedAt`   | string        | Sí       | Fecha y hora de reembolso si fue cancelado (ISO 8601)     |

#### 📤 DTO: PaymentResponse

| Campo               | Tipo          | Nullable | Descripción                                                             |
| ------------------- | ------------- | -------- | ----------------------------------------------------------------------- |
| `id`                | UUID          | No       | Identificador único del pago                                            |
| `orderId`           | UUID          | Sí       | Pedido vinculado (asignado tras pasar a `APPROVED`)                     |
| `tenantId`          | UUID          | No       | Restaurante receptor                                                    |
| `customerId`        | UUID          | Sí       | Cliente comprador                                                       |
| `mpOrderId`         | string        | Sí       | ID de la orden en Mercado Pago (`ORD...`)                               |
| `mpPaymentId`       | string        | Sí       | ID de la transacción de pago en Mercado Pago                            |
| `mpPreferenceId`    | string        | Sí       | ID de Checkout Pro / preferencia                                        |
| `externalReference` | string        | Sí       | Referencia única enviada a Mercado Pago                                 |
| `checkoutUrl`       | string        | Sí       | URL de Checkout Pro (`ticket_url`)                                      |
| `paymentMethod`     | string        | Sí       | Medio de pago utilizado (visa, master, yape, etc.)                      |
| `status`            | PaymentStatus | No       | `PENDING`, `IN_PROCESS`, `APPROVED`, `REJECTED`, `CANCELLED`, `REFUNDED`|
| `statusDetail`      | string        | Sí       | Detalle del estado devuelto por Mercado Pago                            |
| `amount`            | BigDecimal    | No       | Monto total cobrado                                                     |
| `refundedAmount`    | BigDecimal    | Sí       | Monto total reembolsado                                                 |
| `currency`          | string        | No       | Moneda (`PEN`)                                                          |
| `paidAt`            | string        | Sí       | Fecha de aprobación del pago (ISO 8601)                                 |
| `refundedAt`        | string        | Sí       | Fecha de ejecución del reembolso (ISO 8601)                              |
| `expiresAt`         | string        | Sí       | Fecha límite de pago (ISO 8601)                                         |
| `createdAt`         | string        | No       | Fecha de creación (ISO 8601)                                            |
| `updatedAt`         | string        | No       | Fecha de actualización (ISO 8601)                                       |

---

## 8. Inventory — Control de Almacén, Kardex y Recetas

> **🏪 Multi-Tenant:** Todos los endpoints de inventario operan en el contexto de un restaurante (`tenantId`) y requieren membresía activa en el equipo del restaurante. El saldo físico (`quantity`) **solo** se modifica mediante movimientos auditables (`InventoryMovement`), nunca directamente en el CRUD de existencias.

---

### 📦 8.1 Insumos (`InventoryItemController`)

Ruta base: `/api/v1/tenants/{tenantId}/inventory/items`

#### GET `/api/v1/tenants/{tenantId}/inventory/items`

> Lista los insumos del almacén del restaurante de forma paginada con filtros opcionales.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante (`OWNER`, `ADMIN`, `COCINERO`, `REPARTIDOR`) o `SUPERADMIN`

**Query Parameters:**

| Parámetro    | Tipo    | Requerido | Descripción                                    |
| ------------ | ------- | --------- | ---------------------------------------------- |
| `activeOnly` | boolean | No        | Si es `true`, retorna solo insumos activos     |
| `category`   | string  | No        | Filtra por categoría (insensible a mayúsculas) |
| `unit`       | string  | No        | Filtra por unidad de medida (`UNIT` o `KG`)    |
| `page`       | number  | No        | Página (0-indexed, default: 0)                 |
| `size`       | number  | No        | Tamaño de página (default: 20)                 |

##### 📤 Response (200 OK)

Retorna `ApiResponse<PageResponse<InventoryItemResponse>>`.

---

#### GET `/api/v1/tenants/{tenantId}/inventory/items/{itemId}`

> Obtiene el detalle de un insumo del restaurante por su ID.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante

---

#### POST `/api/v1/tenants/{tenantId}/inventory/items`

> Registra un nuevo insumo en el inventario validando unicidad de nombre y SKU por restaurante.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN`  
**Content-Type:** `application/json`

##### 📥 Request (`InventoryItemRequest`)

```json
{
  "name": "Harina de Trigo 00",
  "sku": "INS-HAR-01",
  "description": "Saco de harina italiana para masa madre",
  "category": "Secos",
  "unit": "KG",
  "costPrice": 4.5,
  "isActive": true
}
```

##### 📤 Response (201 Created)

```json
{
  "success": true,
  "message": "Insumo de inventario creado exitosamente",
  "data": {
    "id": "50000000-0000-0000-0000-000000000001",
    "tenantId": "11111111-1111-1111-1111-111111111111",
    "name": "Harina de Trigo 00",
    "sku": "INS-HAR-01",
    "description": "Saco de harina italiana para masa madre",
    "category": "Secos",
    "unit": "KG",
    "costPrice": 4.5,
    "isActive": true,
    "createdAt": "2026-11-05T17:00:00Z",
    "updatedAt": "2026-11-05T17:00:00Z"
  }
}
```

---

#### PUT `/api/v1/tenants/{tenantId}/inventory/items/{itemId}`

> Actualiza los datos de un insumo existente.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN`

---

#### PATCH `/api/v1/tenants/{tenantId}/inventory/items/{itemId}/status`

> Activa o desactiva un insumo (`InventoryItemStatusRequest`: `{ "isActive": false }`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN`

---

#### DELETE `/api/v1/tenants/{tenantId}/inventory/items/{itemId}`

> Elimina un insumo siempre que no tenga un registro de stock ni forme parte de una receta de producto.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN`

---

### 📊 8.2 Existencias de Almacén (`InventoryStockController`)

Ruta base: `/api/v1/tenants/{tenantId}/inventory/stocks`

#### GET `/api/v1/tenants/{tenantId}/inventory/stocks`

> Lista los registros de existencias del restaurante de forma paginada.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante

---

#### GET `/api/v1/tenants/{tenantId}/inventory/stocks/low-stock`

> Obtiene la lista de insumos en alerta de stock crítico (`quantity <= minimumStock`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante

---

#### GET `/api/v1/tenants/{tenantId}/inventory/stocks/{stockId}` y `/item/{itemId}`

> Obtiene el detalle de existencias por `stockId` o por `itemId`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante

---

#### POST `/api/v1/tenants/{tenantId}/inventory/stocks`

> Inicializa el registro de existencias (`quantity = 0.000`) para un insumo activo del restaurante.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN`  
**Content-Type:** `application/json`

##### 📥 Request (`InventoryStockRequest`)

```json
{
  "itemId": "50000000-0000-0000-0000-000000000001",
  "minimumStock": 10.0,
  "location": "Almacén Secos - Estante A1",
  "isActive": true
}
```

##### 📤 Response (201 Created)

```json
{
  "success": true,
  "message": "Registro de existencias creado exitosamente",
  "data": {
    "id": "51000000-0000-0000-0000-000000000001",
    "tenantId": "11111111-1111-1111-1111-111111111111",
    "itemId": "50000000-0000-0000-0000-000000000001",
    "itemName": "Harina de Trigo 00",
    "itemSku": "INS-HAR-01",
    "unit": "KG",
    "quantity": 0.0,
    "reservedQuantity": 0.0,
    "availableQuantity": 0.0,
    "minimumStock": 10.0,
    "lowStock": true,
    "location": "Almacén Secos - Estante A1",
    "isActive": true,
    "createdAt": "2026-11-05T17:00:00Z",
    "updatedAt": "2026-11-05T17:00:00Z"
  }
}
```

---

#### PUT `/api/v1/tenants/{tenantId}/inventory/stocks/{stockId}`

> Actualiza umbral (`minimumStock`), ubicación física (`location`) o estado (`isActive`) sin alterar `quantity`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN`

---

#### GET `/api/v1/tenants/{tenantId}/inventory/stocks/{stockId}/kardex`

> Consulta el Kardex (historial paginado de movimientos ordenado del más reciente al más antiguo) de un registro de stock.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante

---

### 📑 8.3 Movimientos / Kardex (`InventoryMovementController`)

Ruta base: `/api/v1/tenants/{tenantId}/inventory/movements`

#### POST `/api/v1/tenants/{tenantId}/inventory/movements`

> Registra un movimiento de inventario (`ENTRY`, `EXIT`, `ADJUSTMENT`) aplicando bloqueo pesimista (`SELECT ... FOR UPDATE`), validando coherencia `movementType` ↔ `reason`, tomando `performedBy` desde el token JWT e impidiendo saldos negativos.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` o `COCINERO`  
**Content-Type:** `application/json`

##### 📥 Request (`InventoryMovementRequest`)

| Campo           | Tipo                      | Requerido | Validación                                | Descripción                                    |
| --------------- | ------------------------- | --------- | ----------------------------------------- | ---------------------------------------------- |
| `stockId`       | UUID                      | Sí        | UUID válido                               | Registro de stock afectado                     |
| `movementType`  | `InventoryMovementType`   | Sí        | `ENTRY`, `EXIT`, `ADJUSTMENT`             | Tipo de operación                              |
| `reason`        | `InventoryMovementReason` | Sí        | Coherente con `movementType`              | Motivo estandarizado del movimiento            |
| `quantity`      | BigDecimal                | Sí        | `> 0` en ENTRY/EXIT; `!= 0` en ADJUSTMENT | Cantidad del movimiento                        |
| `reasonDetails` | string                    | Sí        | No en blanco                              | Justificación obligatoria de auditoría         |

```json
{
  "stockId": "51000000-0000-0000-0000-000000000001",
  "movementType": "ENTRY",
  "reason": "PURCHASE",
  "quantity": 50.0,
  "reasonDetails": "Factura proveedor F001-443"
}
```

##### 📤 Response (201 Created)

```json
{
  "success": true,
  "message": "Movimiento de inventario registrado exitosamente",
  "data": {
    "id": "52000000-0000-0000-0000-000000000001",
    "tenantId": "11111111-1111-1111-1111-111111111111",
    "stockId": "51000000-0000-0000-0000-000000000001",
    "itemId": "50000000-0000-0000-0000-000000000001",
    "itemName": "Harina de Trigo 00",
    "itemSku": "INS-HAR-01",
    "unit": "KG",
    "movementType": "ENTRY",
    "reason": "PURCHASE",
    "quantity": 50.0,
    "previousQuantity": 0.0,
    "newQuantity": 50.0,
    "performedById": "00000000-0000-0000-0000-000000000002",
    "performedByName": "Mario Rossi",
    "reasonDetails": "Factura proveedor F001-443",
    "createdAt": "2026-11-05T17:05:00Z"
  }
}
```

---

#### GET `/api/v1/tenants/{tenantId}/inventory/movements`

> Lista paginada de movimientos de inventario del restaurante, filtrable por `movementType` y `reason`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante

---

### 🍕 8.4 Recetas de Productos (`ProductIngredientController`)

Ruta base: `/api/v1/tenants/{tenantId}/products/{productId}/ingredients`

Permite configurar los insumos y porciones exactas que consume un producto al venderse.  
> 🔄 **Descuento y Reversión Automática de Kardex:**
> - Cuando una orden avanza al estado `EN_PREPARACION` (KDS), el sistema descuenta automáticamente las cantidades de inventario mediante movimientos `EXIT` con motivo `SALE`.
> - Si un pedido se cancela desde `EN_PREPARACION`, se repone automáticamente el inventario mediante movimientos `ENTRY` con motivo `CUSTOMER_RETURN`.

---

#### GET `/api/v1/tenants/{tenantId}/products/{productId}/ingredients`

> Lista todos los insumos (receta) configurados para una unidad del producto del restaurante.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante (`OWNER`, `ADMIN`, `COCINERO`, `REPARTIDOR`) o `SUPERADMIN`

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Receta del producto obtenida exitosamente",
  "data": [
    {
      "id": "1a2b3c4d-5e6f-7890-abcd-ef1234567890",
      "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "productId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "productName": "Hamburguesa Doble Queso",
      "inventoryItemId": "7fa85f64-5717-4562-b3fc-2c963f66afa7",
      "inventoryItemName": "Carne de Res Molida",
      "inventoryItemSku": "INS-CARNE-01",
      "unit": "KG",
      "quantity": 0.250,
      "createdAt": "2026-11-05T15:00:00Z",
      "updatedAt": "2026-11-05T15:00:00Z"
    }
  ]
}
```

---

#### POST `/api/v1/tenants/{tenantId}/products/{productId}/ingredients`

> Agrega o actualiza la cantidad de un insumo requerida para la preparación de una unidad del producto. Si el insumo ya formaba parte de la receta, se actualiza la cantidad asignada.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON) — `ProductIngredientRequest`:**

| Campo             | Tipo       | Requerido | Validación | Descripción                                  |
| ----------------- | ---------- | --------- | ---------- | -------------------------------------------- |
| `inventoryItemId` | UUID       | Sí        | UUID       | Identificador del insumo del almacén         |
| `quantity`        | BigDecimal | Sí        | ≥ 0.001    | Cantidad consumida por unidad de platillo    |

```json
{
  "inventoryItemId": "7fa85f64-5717-4562-b3fc-2c963f66afa7",
  "quantity": 0.250
}
```

##### 📤 Response

**Éxito (201 Created):** Retorna `ApiResponse<ProductIngredientResponse>`.

---

#### DELETE `/api/v1/tenants/{tenantId}/products/{productId}/ingredients/{ingredientId}`

> Elimina un insumo de la receta del producto. A partir de ese momento, la venta del platillo dejará de descontar existencias de dicho insumo.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Ingrediente eliminado de la receta exitosamente"
}
```

---

### 📦 Modelos de Datos / DTOs de Recetas

#### 📥 DTO: ProductIngredientRequest

| Campo             | Tipo       | Requerido | Validación | Descripción                               |
| ----------------- | ---------- | --------- | ---------- | ----------------------------------------- |
| `inventoryItemId` | UUID       | Sí        | UUID       | Insumo del inventario                     |
| `quantity`        | BigDecimal | Sí        | ≥ 0.001    | Cantidad requerida por porción del plato  |

#### 📤 DTO: ProductIngredientResponse

| Campo               | Tipo          | Nullable | Descripción                                      |
| ------------------- | ------------- | -------- | ------------------------------------------------ |
| `id`                | UUID          | No       | Identificador del ingrediente en la receta       |
| `tenantId`          | UUID          | No       | Restaurante                                      |
| `productId`         | UUID          | No       | Producto al que pertenece la receta              |
| `productName`       | string        | No       | Nombre del producto                              |
| `inventoryItemId`   | UUID          | No       | Insumo vinculado                                 |
| `inventoryItemName` | string        | No       | Nombre del insumo                                |
| `inventoryItemSku`  | string        | Sí       | Código SKU del insumo                            |
| `unit`              | InventoryUnit | No       | Unidad de medida (`UNIT`, `KG`, `LT`)            |
| `quantity`          | BigDecimal    | No       | Cantidad requerida                               |
| `createdAt`         | string        | No       | ISO 8601                                         |
| `updatedAt`         | string        | No       | ISO 8601                                         |

---

## 9. Tracking — Rastreo Geoespacial (PostGIS) y Tiempo Estimado (ETA)

> **Motor Espacial PostGIS (`geometry(Point, 4326)`):** Módulo de cálculo geodésico y monitoreo en tiempo real estilo Rappi o PedidosYa:
> 1. **Estimación Previa al Checkout:** Calcula distancia ortodrómica (`ST_DistanceSphere`) y distancia vial estimada multiplicada por el factor de ruta (`roadFactor = 1.3`), estimando tiempo de preparación y recorrido (`[minMinutes, maxMinutes]`) antes de ordenar.
> 2. **Cálculo Automático de ETA al Despachar:** Al asignar el repartidor y pasar el pedido a `EN_CAMINO`, genera automáticamente la hora prevista de llegada (`estimatedArrivalAt`) y el mensaje amigable de ETA.
> 3. **Telemetría GPS en Vivo con Throttling:** La aplicación móvil del repartidor envía pings GPS (`PUT .../tracking/location`) con rumbo (`heading`) y velocidad. El backend aplica *throttling* de 5 segundos, filtro de saltos anómalos y recálculo dinámico en cada ping.
> 4. **Polling Optimizado para Frontend:** Los clientes consultan periódicamente (`GET .../orders/{id}/tracking`) siguiendo el intervalo sugerido (`pollIntervalSeconds = 15`), con banderas `trackingActive` y `stale` para una experiencia de usuario fluida e interpolación en mapa (Google Maps / Mapbox).

---

### 🌐 9.1 Storefront & Clientes (`StorefrontTrackingController`)

Ruta base: `/api/v1/store/{slug}`

---

#### GET `/api/v1/store/{slug}/delivery-estimate`

> Verifica si una ubicación se encuentra dentro del radio de entrega del restaurante (`deliveryRadiusKm`) y calcula la distancia vial estimada, tarifa de envío y rango estimado de entrega en minutos.

**Autenticación:** No requerida (Público)  
**Rol requerido:** Ninguno  
**Query Parameters:**

| Parámetro | Tipo   | Requerido | Descripción                     |
| --------- | ------ | --------- | ------------------------------- |
| `lat`     | Double | Sí        | Latitud de entrega del cliente  |
| `lng`     | Double | Sí        | Longitud de entrega del cliente |

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Estimación de entrega calculada exitosamente",
  "data": {
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantSlug": "burger-bros",
    "tenantName": "Burger Bros",
    "withinCoverage": true,
    "maxDeliveryRadiusKm": 6.5,
    "straightDistanceKm": 2.45,
    "estimatedRouteDistanceKm": 3.18,
    "deliveryFee": 5.00,
    "estimatedDeliveryMinutes": 24,
    "minMinutes": 20,
    "maxMinutes": 30,
    "estimatedArrivalAt": "2026-11-10T15:24:00Z",
    "message": "Tiempo estimado de entrega: 20-30 min"
  }
}
```

---

#### GET `/api/v1/store/{slug}/orders/{orderId}/tracking`

> Permite al comensal consultar el estado de seguimiento de su pedido en tiempo real. Cuando la orden pasa a `EN_CAMINO`, expone las coordenadas actuales del repartidor, su rumbo, velocidad, distancia restante y el ETA actualizado.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cliente dueño de la orden

##### 📤 Response

**Éxito (200 OK):**

```json
{
  "success": true,
  "message": "Seguimiento del pedido obtenido exitosamente",
  "data": {
    "orderId": "1fa85f64-5717-4562-b3fc-2c963f66afa1",
    "orderNumber": "ORD-000142",
    "orderStatus": "EN_CAMINO",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantName": "Burger Bros",
    "storeLatitude": -12.121543,
    "storeLongitude": -77.028941,
    "deliveryAddress": "Av. Las Begonias 441, San Isidro",
    "destinationLatitude": -12.093124,
    "destinationLongitude": -77.026451,
    "driverId": "5fa85f64-5717-4562-b3fc-2c963f66afa8",
    "driverName": "Pedro Repartidor",
    "driverPhone": "+51999111222",
    "driverLatitude": -12.105432,
    "driverLongitude": -77.027812,
    "heading": 355.4,
    "speedKmh": 28.5,
    "accuracyM": 6.2,
    "remainingDistanceKm": 1.42,
    "etaMinutes": 8,
    "etaMinMinutes": 6,
    "etaMaxMinutes": 10,
    "estimatedArrivalAt": "2026-11-10T15:08:00Z",
    "etaMessage": "Tu pedido estará en tu puerta en ~8 min",
    "trackingActive": true,
    "stale": false,
    "throttled": false,
    "pollIntervalSeconds": 15,
    "lastUpdatedAt": "2026-11-10T15:00:12Z",
    "dispatchedAt": "2026-11-10T14:52:00Z",
    "deliveredAt": null,
    "routeHistory": []
  }
}
```

---

### 🏍️ 9.2 Repartidor & Backoffice (`DeliveryTrackingController`)

Ruta base: `/api/v1/tenants/{tenantId}`

---

#### PUT `/api/v1/tenants/{tenantId}/orders/{orderId}/tracking/location`

> Envía un ping de ubicación GPS del repartidor para una orden en curso. Actualiza la posición en memoria y BD, recalcula en vivo el ETA restante y registra el punto en el historial de ruta.
>
> ⚠️ **Frecuencia Recomendada:** La app móvil del repartidor debe enviar coordenadas cada 10 a 15 segundos. Peticiones con menos de 5 segundos de diferencia son admitidas con bandera `throttled = true` sin sobrecargar la base de datos.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `REPARTIDOR` asignado al pedido, `OWNER` o `ADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON) — `DriverLocationUpdateRequest`:**

| Campo       | Tipo   | Requerido | Validación        | Descripción                                 |
| ----------- | ------ | --------- | ----------------- | ------------------------------------------- |
| `latitude`  | Double | Sí        | Entre -90 y 90    | Latitud actual reportada por el dispositivo |
| `longitude` | Double | Sí        | Entre -180 y 180  | Longitud actual reportada                   |
| `heading`   | Double | No        | Entre 0 y 360     | Rumbo / dirección de desplazamiento         |
| `speedKmh`  | Double | No        | 0.0 a 200.0       | Velocidad instantánea en km/h               |
| `accuracyM` | Double | No        | ≥ 0.0             | Precisión del sensor GPS en metros          |

```json
{
  "latitude": -12.105432,
  "longitude": -77.027812,
  "heading": 355.4,
  "speedKmh": 28.5,
  "accuracyM": 6.2
}
```

##### 📤 Response

**Éxito (200 OK):** Retorna `ApiResponse<DeliveryTrackingResponse>`.

---

#### GET `/api/v1/tenants/{tenantId}/orders/{orderId}/tracking`

> Obtiene el estado de seguimiento de una orden para el equipo del restaurante. Con el parámetro opcional `includeHistory=true`, incluye el array completo de puntos GPS recorridos (`routeHistory`) para renderizar la polilínea en el mapa.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro del restaurante (`OWNER`, `ADMIN`, `COCINERO`, `REPARTIDOR`)  
**Query Parameters:**
- `includeHistory` (boolean, default: `false`): Si es `true`, devuelve todos los puntos del trayecto en `routeHistory`.

##### 📤 Response

**Éxito (200 OK):** Retorna `ApiResponse<DeliveryTrackingResponse>`.

---

#### GET `/api/v1/tenants/{tenantId}/tracking/active`

> Devuelve la lista en tiempo real de todos los repartidores del restaurante con pedidos actualmente en ruta (`EN_CAMINO`), con su última posición GPS reportada y ETA hacia el destino. Diseñado para el panel de despacho del restaurante.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante (o `SUPERADMIN`)

##### 📤 Response

**Éxito (200 OK):** Retorna `ApiResponse<List<DeliveryTrackingResponse>>`.

---

### 📦 Modelos de Datos / DTOs de Tracking

#### 📥 DTO: DriverLocationUpdateRequest

| Campo       | Tipo   | Requerido | Validación        | Descripción                         |
| ----------- | ------ | --------- | ----------------- | ----------------------------------- |
| `latitude`  | Double | Sí        | Entre -90 y 90    | Latitud reportada                   |
| `longitude` | Double | Sí        | Entre -180 y 180  | Longitud reportada                  |
| `heading`   | Double | No        | Entre 0 y 360     | Grados de orientación de la brújula |
| `speedKmh`  | Double | No        | 0 a 200           | Velocidad en km/h                   |
| `accuracyM` | Double | No        | ≥ 0               | Margen de error GPS en metros       |

#### 📤 DTO: DeliveryEstimateResponse

| Campo                      | Tipo       | Nullable | Descripción                                            |
| -------------------------- | ---------- | -------- | ------------------------------------------------------ |
| `tenantId`                 | UUID       | No       | Restaurante evaluado                                   |
| `tenantSlug`               | string     | No       | Slug de la tienda                                      |
| `tenantName`               | string     | No       | Nombre del restaurante                                 |
| `withinCoverage`           | boolean    | No       | `true` si la ubicación está dentro del radio permitido |
| `maxDeliveryRadiusKm`      | BigDecimal | No       | Radio máximo configurado en el restaurante             |
| `straightDistanceKm`       | BigDecimal | No       | Distancia lineal calculada con PostGIS                 |
| `estimatedRouteDistanceKm` | BigDecimal | No       | Distancia vial estimada (`distancia × 1.3`)            |
| `deliveryFee`              | BigDecimal | No       | Costo de envío aplicable                               |
| `estimatedDeliveryMinutes` | number     | No       | Minutos totales estimados (cocina + viaje)             |
| `minMinutes`               | number     | No       | Límite inferior del rango de entrega                   |
| `maxMinutes`               | number     | No       | Límite superior del rango de entrega                   |
| `estimatedArrivalAt`       | string     | No       | Fecha y hora estimada de llegada (ISO 8601)            |
| `message`                  | string     | No       | Mensaje resumen para el comensal                       |

#### 📤 DTO: DeliveryTrackingResponse

| Campo                 | Tipo                       | Nullable | Descripción                                                  |
| --------------------- | -------------------------- | -------- | ------------------------------------------------------------ |
| `orderId`             | UUID                       | No       | Pedido rastreado                                             |
| `orderNumber`         | string                     | No       | Código legible del pedido (ej. "ORD-000142")                 |
| `orderStatus`         | OrderStatus                | No       | Estado del pedido (`EN_CAMINO`, `ENTREGADO`, etc.)           |
| `tenantId`            | UUID                       | No       | Restaurante                                                  |
| `tenantName`          | string                     | No       | Nombre del restaurante                                       |
| `storeLatitude`       | Double                     | No       | Latitud de origen (local)                                    |
| `storeLongitude`      | Double                     | No       | Longitud de origen (local)                                   |
| `deliveryAddress`     | string                     | Sí       | Dirección de destino                                         |
| `destinationLatitude` | Double                     | Sí       | Latitud de destino (cliente)                                 |
| `destinationLongitude`| Double                     | Sí       | Longitud de destino (cliente)                                |
| `driverId`            | UUID                       | Sí       | ID del repartidor asignado                                   |
| `driverName`          | string                     | Sí       | Nombre del repartidor                                        |
| `driverPhone`         | string                     | Sí       | Teléfono del repartidor                                      |
| `driverLatitude`      | Double                     | Sí       | Última latitud GPS del repartidor                            |
| `driverLongitude`     | Double                     | Sí       | Última longitud GPS del repartidor                           |
| `heading`             | Double                     | Sí       | Rumbo en grados (0° Norte, 90° Este, 180° Sur, 270° Oeste)   |
| `speedKmh`            | Double                     | Sí       | Velocidad en km/h                                            |
| `accuracyM`           | Double                     | Sí       | Precisión GPS en metros                                      |
| `remainingDistanceKm` | BigDecimal                 | Sí       | Distancia restante al destino                                |
| `etaMinutes`          | number                     | Sí       | Minutos restantes estimados                                  |
| `etaMinMinutes`       | number                     | Sí       | Rango mínimo de minutos                                      |
| `etaMaxMinutes`       | number                     | Sí       | Rango máximo de minutos                                      |
| `estimatedArrivalAt`  | string                     | Sí       | Hora estimada de llegada (ISO 8601)                          |
| `etaMessage`          | string                     | Sí       | Mensaje formateado para el cliente                           |
| `trackingActive`      | boolean                    | No       | `true` si el rastreo está activo (`EN_CAMINO`)               |
| `stale`               | boolean                    | No       | `true` si el último ping GPS tiene más de 3 minutos de atraso|
| `throttled`           | boolean                    | No       | `true` si la última petición fue throttled                   |
| `pollIntervalSeconds` | number                     | No       | Frecuencia recomendada de consulta para el frontend (15 s)   |
| `lastUpdatedAt`       | string                     | Sí       | Timestamp del último ping GPS recibido                       |
| `dispatchedAt`        | string                     | Sí       | Timestamp en que la orden salió a ruta                       |
| `deliveredAt`         | string                     | Sí       | Timestamp en que se confirmó la entrega                      |
| `routeHistory`        | List<TrackingPointResponse>| No       | Historial ordenado cronológicamente de posiciones GPS        |

#### 📤 DTO: TrackingPointResponse

| Campo        | Tipo   | Nullable | Descripción                     |
| ------------ | ------ | -------- | ------------------------------- |
| `latitude`   | Double | No       | Latitud registrada              |
| `longitude`  | Double | No       | Longitud registrada             |
| `heading`    | Double | Sí       | Rumbo en grados                 |
| `speedKmh`   | Double | Sí       | Velocidad en km/h               |
| `accuracyM`  | Double | Sí       | Precisión del sensor en metros  |
| `recordedAt` | string | No       | Timestamp del punto (ISO 8601)  |

---

## 📝 Notas Especiales para el Frontend

1. **Tokens en Headers:** Enviar siempre el token mediante el header HTTP `Authorization: Bearer <accessToken>`. No se usan cookies ni query params para el transporte de credenciales.
2. **UUIDs como Strings:** Todos los identificadores son cadenas UUID de 36 caracteres. No aplicar parseo a tipo número.
3. **Manejo de Fechas:** Las fechas viajan con zona horaria ISO 8601 (ej. `2026-10-06T14:30:00Z`). Para mostrarlas al usuario final, utilizar librerías como `date-fns` o `Intl.DateTimeFormat`.
4. **Geolocalización WGS84:** Las coordenadas geográficas utilizan el estándar WGS84 (SRID 4326): `latitude` y `longitude` como números en coma flotante (`Double`).
5. **CORS:** Durante el desarrollo local, los orígenes `http://localhost:3000` y `http://localhost:5173` están admitidos por defecto en el backend.
6. **Validación de Errores Jakarta:** En errores `400 Bad Request`, el campo `message` reúne las causas de validación concatenadas (ej. `"email: Formato de email inválido, password: La contraseña debe tener al menos 8 caracteres"`).
