# 📖 NexoFood API — Documentación para Frontend

> **Proyecto:** NexoFood API — Plataforma SaaS Multi-Tenant para Restaurantes  
> **Stack:** Java 21 · Spring Boot 4.1.1 · PostgreSQL 16 + PostGIS · Hibernate Spatial · Caffeine Cache · Bucket4j  
> **Versión:** 1.0  
> **Última actualización:** 2026-11-23  
> **Guía de estilo:** [DOCUMENTATION_GUIDE.md](./DOCUMENTATION_GUIDE.md)

---

## 📌 Información General

| Campo | Valor |
| --- | --- |
| **Base URL (Local)** | `http://localhost:8080/api/v1` |
| **Base URL (Prod)** | `https://api.nexofood-app.com/api/v1` |
| **Formato** | `application/json` *(y `text/event-stream` para el stream SSE de notificaciones)* |
| **Autenticación** | JWT Bearer Token en header `Authorization` |
| **Versión** | `v1` |
| **Documentación viva (Swagger UI)** | `http://localhost:8080/swagger-ui/index.html` *(habilitado solo en perfil `dev`; deshabilitado en `prod`)* |
| **OpenAPI JSON** | `http://localhost:8080/v3/api-docs` *(solo `dev`)* |

> **📝 Nota sobre URLs de Monitoreo (Actuator):** Los endpoints `/actuator/health` y `/actuator/info` son públicos (sin prefijo `/api/v1`) para healthchecks; el resto de `/actuator/**` está restringido a `SUPERADMIN`.

---

## 🏪 Arquitectura Multi-Tenant

NexoFood es una plataforma **SaaS multi-tenant** donde cada restaurante es un **Tenant** independiente con su propia marca, horarios de atención, catálogo, stock, configuración tributaria, staff, clientes, pedidos, pagos y rastreo geoespacial:

```text
Usuario (User)
  ├── Notificaciones (Notifications: invitaciones, alertas de stock/horarios/suscripción y pedidos)
  └── Suscripción (Subscription) ← vinculada a un Plan SaaS (SubscriptionPlan)
        └── Tenant (Restaurante) ← relación 1:1 con suscripción y propietario (owner) + horario (openingTime/closingTime, isOpen)
              ├── Impuestos (Taxes: IGV 18%, Exonerado 0%, inclusivos o exclusivos)
              ├── Equipo / Staff (TenantMembers + TenantInvitations por correo: OWNER, ADMIN, COCINERO, REPARTIDOR)
              ├── Clientes de Tienda (TenantCustomer: puntos de lealtad, bloqueo, notas)
              ├── Catálogo: Categorías → Productos (stock directo) → Reglas de Precio/Promociones
              ├── Carritos (1 único carrito por combinación tenant + customer)
              ├── Pedidos (Orders con numeración secuencial ORD-XXXXXX y snapshot fiscal inmutable)
              ├── Pagos (Mercado Pago OAuth directo al restaurante + Checkout Pro Orders API v1)
              └── Tracking (Cobertura PostGIS, ETA dinámico y telemetría GPS en vivo)
```

### Reglas Multi-Tenant Clave para el Frontend

1. **Cada restaurante es un `Tenant`** con su propio catálogo (incluyendo stock por producto), horarios, pedidos, pagos y staff.
2. **Un usuario puede ser dueño (`OWNER`) de un solo tenant** (relación 1 a 1 estricta) y requiere una suscripción vigente (`TRIAL` o `ACTIVE`) para crearlo.
3. **Los productos, categorías e impuestos pertenecen a un tenant específico.** Los endpoints de backoffice operan bajo `/api/v1/tenants/{tenantId}/...`, mientras que el storefront público y de comensales opera bajo `/api/v1/store/{slug}/...`.
4. **Los carritos son únicos por combinación `(tenant, customer)`** — un cliente tiene un carrito independiente por cada restaurante.
5. **Los pedidos `DELIVERY` validan cobertura geoespacial (PostGIS WGS84 / SRID 4326)** — si la dirección del cliente excede `deliveryRadiusKm` del restaurante, el servidor rechaza el checkout con `400 Bad Request`.
6. **Guardia Anti-Fraude en Cocina:** Un pedido no puede pasar de `PENDIENTE` a `EN_PREPARACION` sin un pago confirmado (`APPROVED`).
7. **Horarios de Atención y Cierre Automático:** Un restaurante puede definir `openingTime` y `closingTime`; el scheduler automático actualiza `isOpen` y emite alertas previas (`STORE_OPENING_SOON`, `STORE_CLOSING_SOON`). Si el local está cerrado (`isOpen = false` o fuera de horario), cualquier intento de checkout o creación de pedido se rechaza con `400 Bad Request`.
8. **Invitaciones por Correo y Notificaciones Cross-Tenant:** Invitar personal por `email` crea una `TenantInvitation` en estado `PENDING` y notifica al usuario (`STAFF_INVITATION`); la membresía `TenantMember` se crea únicamente cuando el usuario responde `ACCEPT`. Las notificaciones e invitaciones del usuario se consultan de forma global entre tiendas bajo `/api/v1/notifications` y `/api/v1/invitations`.

---

## 🔐 Autenticación y Autorización

### 🔑 Autenticación

- **Tipo:** JWT (JSON Web Token) con firma HMAC-SHA256 (claves independientes para access y refresh)
- **Envío:** Header `Authorization: Bearer <accessToken>`
- **Access Token:** Expira en 15 minutos (`900` segundos)
- **Refresh Token:** Expira en 7 días (`604800` segundos), con rotación automática en cada renovación
- **Renovación:** `POST /api/v1/auth/refresh` enviando `{ "refreshToken": "..." }`
- **Cierre de sesión:** `POST /api/v1/auth/logout` enviando `{ "refreshToken": "..." }` (requiere Access Token vigente)
- **Expiración:** El servidor responde con `401 Unauthorized`
- **Seguridad:** Si se detecta reutilización de un refresh token revocado, se revocan **TODOS** los tokens del usuario. Al cambiar la contraseña o eliminar la cuenta, se incrementa `tokenVersion`, invalidando de inmediato incluso los access tokens activos.

> **⚠️ Nota para el frontend:**
> - El token se envía en el header `Authorization`, **NO** en cookies.
> - El frontend es responsable de almacenar los tokens de forma segura (memoria/estado de la app + almacenamiento seguro según plataforma) y de implementar un interceptor para renovar ante `401 Unauthorized`.
> - Configurar `Authorization: Bearer ${accessToken}` en cada request autenticada.
> - **Política de contraseñas:** Entre 8 y 100 caracteres, al menos 1 letra y 1 número, sin espacios.
> - **Bloqueo temporal:** 5 intentos fallidos de inicio de sesión bloquean la cuenta por **15 minutos** (`401 Unauthorized`).
> - **Rate Limiting:** Los endpoints `POST /api/v1/auth/(login|register|refresh)` y `POST /api/v1/store/{slug}/auth/(login|register)` están limitados a **10 solicitudes cada 60 segundos** por IP y grupo de endpoint (headers `X-Rate-Limit-Remaining` y `Retry-After` ante `429 Too Many Requests`).

### Claims del Access Token

| Claim | Tipo | Descripción |
| --- | --- | --- |
| `sub` | string | Correo electrónico del usuario (normalizado en minúsculas) |
| `iss` | string | Emisor del token (`nexofood-api`) |
| `aud` | string | Audiencia (`nexofood-client`) |
| `type` | string | Tipo de token: `"access"` o `"refresh"` |
| `role` | string | Rol global del sistema (`"SUPERADMIN"` o `"USER"`) |
| `userId` | string (UUID) | Identificador único del usuario |
| `tokenVersion` | number | Versión de sesión para revocación inmediata |
| `iat` | number | Timestamp UNIX de emisión |
| `exp` | number | Timestamp UNIX de expiración |

### Roles del Sistema y Roles de Staff

- **Roles globales (`UserSystemRole`):**
  - `SUPERADMIN`: Administrador global de la plataforma (acceso total a backoffice administrativo y bypass de verificación de membresía en cualquier tenant).
  - `USER`: Usuario estándar (puede actuar como comensal, propietario de 1 restaurante o empleado en uno o más restaurantes).
- **Roles de staff en restaurante (`TenantStaffRole`):**
  - `OWNER`: Propietario con control total del restaurante (incluye configuración, pagos Mercado Pago, miembros y pedidos).
  - `ADMIN`: Administrador delegado (catálogo, clientes, pedidos, pagos, miembros no-ADMIN/no-OWNER).
  - `COCINERO`: Consulta de catálogo/órdenes, cola KDS y transiciones a `EN_PREPARACION` y `LISTO_PARA_ENTREGA`.
  - `REPARTIDOR`: Consulta de sus entregas asignadas, transiciones a `EN_CAMINO` y `ENTREGADO`, y envío de pings GPS.

---

## 📊 Envelopes de Respuesta

La API utiliza **dos envelopes estándar** para todas las respuestas (salvo excepciones puntuales documentadas como los webhooks de Mercado Pago):

### ✅ Envelope de éxito: `ApiResponse<T>`

```json
{
  "success": true,
  "message": "Mensaje descriptivo del resultado",
  "data": {}
}
```

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `success` | boolean | Siempre `true` en respuestas exitosas |
| `message` | string | Mensaje legible para la UI o logs |
| `data` | T | Payload del recurso (objeto, lista, `PageResponse<T>`, o `null` en operaciones sin retorno) |

### 📄 Envelope de paginación: `PageResponse<T>`

Utilizado dentro de `ApiResponse.data` en endpoints paginados (acepta query params `page` 0-indexed, `size` default `20`, y `sort`):

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

> **⚠️ Excepción de paginación:** El endpoint `GET /api/v1/admin/tenants/{tenantId}/customers` retorna directamente `org.springframework.data.domain.Page<TenantCustomerResponse>` dentro de `ApiResponse.data` en lugar de `PageResponse<T>`.

### ❌ Envelope de error: `ErrorResponse`

```json
{
  "message": "Descripción del error",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/ruta",
  "method": "POST"
}
```

| Campo | Tipo | Descripción |
| --- | --- | --- |
| `message` | string | Descripción legible del error |
| `error` | string | Nombre del estado HTTP (`BAD_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `BAD_GATEWAY`, etc.) |
| `status` | number | Código de estado HTTP |
| `timestamp` | string | Fecha y hora del error (ISO 8601 local) |
| `path` | string | Ruta del endpoint que generó el error |
| `method` | string | Método HTTP de la petición *(puede ser `null` en errores generados por filtros de seguridad como `401`/`403`/`429`)* |

---

## 🟢 Estado de Módulos y Endpoints (Activos vs. Preparados para Futuro)

Todos los módulos principales de la v1 se encuentran **activos y operativos**:

| # | Módulo (Bounded Context) | Estado | Controladores Activos |
| --- | --- | --- | --- |
| 1 | **Identity** | ✅ Activo | `AuthController`, `UserController`, `CustomerAddressController`, `AdminUserController` |
| 2 | **Subscription** | ✅ Activo | `SubscriptionPlanController`, `AdminSubscriptionPlanController`, `SubscriptionController` |
| 3 | **Tenant / Store** | ✅ Activo | `TenantController`, `AdminTenantController`, `TenantMemberController`, `TenantInvitationController`, `TenantCustomerController`, `StoreAuthController` |
| 4 | **Catalog** | ✅ Activo | `TaxController`, `CategoryController`, `ProductController`, `ProductPriceController`, `StorefrontCatalogController` |
| 5 | **Cart** | ✅ Activo | `CartController` |
| 6 | **Order** | ✅ Activo | `StorefrontOrderController`, `OrderController` |
| 7 | **Payment** | ✅ Activo | `MercadoPagoConnectionController`, `MercadoPagoOAuthCallbackController`, `StorefrontPaymentController`, `TenantPaymentController`, `MercadoPagoWebhookController` |
| 8 | **Tracking** | ✅ Activo | `StorefrontTrackingController`, `DeliveryTrackingController` |
| 9 | **Notification** | ✅ Activo | `NotificationController` |

> **🔮 Preparados internamente / No expuestos como endpoints REST aún:**
> - **Creación directa de órdenes sin carrito (`OrderCreateRequest` / `OrderItemRequest`):** Existen en el código para uso interno del flujo de checkout y pruebas, pero el flujo público de compra se realiza exclusivamente vía `POST /api/v1/store/{slug}/orders/checkout`.
> - **Actualización combinada de cliente (`TenantCustomerUpdateRequest`):** Existe en el paquete DTO del módulo `store`, pero actualmente las mutaciones se exponen separadas en `/block`, `/notes` y `/points`.
> - **Búsqueda global pública / marketplace de restaurantes:** El listado paginado de todas las tiendas (`GET /api/v1/admin/tenants` o `/api/v1/admin/stores`) es exclusivo para `SUPERADMIN`; en el storefront público el acceso sigue siendo directo por `slug`.

---

# 📡 Endpoints de la API

---

## 1. Identity — Autenticación, Usuarios y Direcciones

Módulo encargado de registro, inicio de sesión, rotación/revocación de tokens JWT, perfil global del usuario, libreta de direcciones con coordenadas WGS84 y administración global de cuentas.

---

### 🔐 Autenticación (`AuthController`)

#### GET `/api/v1/auth/me`

> Obtiene el perfil completo del usuario autenticado, incluyendo sus membresías de staff en restaurantes y sus direcciones guardadas.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado  
**Content-Type:** `application/json`

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<UserProfileResponse>`:**
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
        "createdAt": "2026-11-18T14:00:00Z",
        "updatedAt": "2026-11-18T14:00:00Z"
      }
    ]
  }
}
```

**Error (`401` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No autorizado: token inválido, expirado o ausente",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/auth/me",
  "method": null
}
```

---

#### POST `/api/v1/auth/register`

> Registra una nueva cuenta de usuario global (`USER`) y devuelve los tokens JWT iniciales.

**Autenticación:** No requerida *(Rate-limited)*  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON) — `UserRegisterRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `email` | string | Sí | Email válido, máx. 255 caracteres | Correo electrónico único (normalizado a minúsculas) |
| `password` | string | Sí | 8–100 chars, ≥1 letra, ≥1 número, sin espacios | Contraseña de la cuenta |
| `fullName` | string | Sí | No vacío, máx. 150 caracteres | Nombre completo |
| `phone` | string | No | Máx. 20 caracteres | Teléfono de contacto |

**Ejemplo de request:**
```json
{
  "email": "carlos@ejemplo.com",
  "password": "Password123!",
  "fullName": "Carlos Rodríguez",
  "phone": "+51999888777"
}
```

##### 📤 Response

**Éxito (`201 Created`) — Envelope `ApiResponse<AuthResponse>`:**
```json
{
  "success": true,
  "message": "Usuario registrado exitosamente",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "email": "carlos@ejemplo.com",
      "fullName": "Carlos Rodríguez",
      "phone": "+51999888777",
      "systemRole": "USER",
      "isActive": true,
      "createdAt": "2026-11-18T14:00:00Z",
      "updatedAt": "2026-11-18T14:00:00Z"
    },
    "staffMemberships": null
  }
}
```

**Error (`400` | `409` | `429`) — Envelope `ErrorResponse`:**
```json
{
  "message": "El correo electrónico ya está registrado",
  "error": "CONFLICT",
  "status": 409,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/auth/register",
  "method": "POST"
}
```

---

#### POST `/api/v1/auth/login`

> Autentica con credenciales globales, revoca los refresh tokens previos del usuario y devuelve nuevos tokens junto con sus membresías de staff.

**Autenticación:** No requerida *(Rate-limited; bloqueo temporal de 15 min tras 5 intentos fallidos)*  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON) — `LoginRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `email` | string | Sí | Email válido, máx. 255 caracteres | Correo electrónico |
| `password` | string | Sí | No vacío, máx. 100 caracteres | Contraseña del usuario |

**Ejemplo de request:**
```json
{
  "email": "carlos@ejemplo.com",
  "password": "Password123!"
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<AuthResponse>`:**
```json
{
  "success": true,
  "message": "Inicio de sesión exitoso",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "email": "carlos@ejemplo.com",
      "fullName": "Carlos Rodríguez",
      "phone": "+51999888777",
      "systemRole": "USER",
      "isActive": true,
      "createdAt": "2026-11-18T14:00:00Z",
      "updatedAt": "2026-11-18T14:00:00Z"
    },
    "staffMemberships": [
      {
        "tenantId": "b1a2c3d4-e5f6-7890-abcd-ef1234567890",
        "tenantName": "Pizzería Nápoles",
        "tenantSlug": "pizzeria-napoles",
        "staffRole": "OWNER"
      }
    ]
  }
}
```

**Error (`400` | `401` | `429`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Las credenciales proporcionadas son incorrectas",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/auth/login",
  "method": "POST"
}
```

---

#### POST `/api/v1/auth/refresh`

> Renueva el `accessToken` y rota el `refreshToken` de forma obligatoria. Si el token enviado ya había sido revocado, revoca todos los tokens del usuario.

**Autenticación:** No requerida *(Rate-limited)*  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON) — `RefreshTokenRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `refreshToken` | string | Sí | No vacío | Refresh token vigente |

**Ejemplo de request:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<AuthResponse>`:**
```json
{
  "success": true,
  "message": "Token renovado exitosamente",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...(nuevo)",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...(nuevo rotado)",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "email": "carlos@ejemplo.com",
      "fullName": "Carlos Rodríguez",
      "phone": "+51999888777",
      "systemRole": "USER",
      "isActive": true,
      "createdAt": "2026-11-18T14:00:00Z",
      "updatedAt": "2026-11-18T14:00:00Z"
    },
    "staffMemberships": null
  }
}
```

**Error (`400` | `401` | `429`) — Envelope `ErrorResponse`:**
```json
{
  "message": "El token de refresco es inválido o ha expirado",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/auth/refresh",
  "method": "POST"
}
```

---

#### POST `/api/v1/auth/logout`

> Revoca el refresh token indicado para cerrar la sesión del usuario autenticado.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON) — `RefreshTokenRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `refreshToken` | string | Sí | No vacío | Refresh token a revocar |

**Ejemplo de request:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<Void>`:**
```json
{
  "success": true,
  "message": "Sesión cerrada exitosamente",
  "data": null
}
```

**Error (`400` | `401`) — Envelope `ErrorResponse`:**
```json
{
  "message": "El token de refresco es inválido o ha expirado",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/auth/logout",
  "method": "POST"
}
```

---

### 👤 Perfil y Cuenta de Usuario (`UserController`)

#### PUT `/api/v1/users/me`

> Actualiza el nombre completo y el teléfono del usuario autenticado. Enviar `phone: null` limpia el teléfono guardado.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON) — `UserUpdateRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `fullName` | string | Sí | No vacío, máx. 150 caracteres | Nombre completo |
| `phone` | string | No | Máx. 20 caracteres | Teléfono de contacto |

**Ejemplo de request:**
```json
{
  "fullName": "Carlos Rodríguez Pérez",
  "phone": "+51999888777"
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<UserResponse>`:**
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
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T15:30:00Z"
  }
}
```

**Error (`400` | `401`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Error de validación: fullName: El nombre completo es obligatorio",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T15:30:00",
  "path": "/api/v1/users/me",
  "method": "PUT"
}
```

---

#### PATCH `/api/v1/users/me/password`

> Cambia la contraseña del usuario autenticado. Incrementa `tokenVersion` y revoca todos sus refresh tokens activos (el frontend debe solicitar un nuevo login).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON) — `ChangePasswordRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `currentPassword` | string | Sí | No vacío, máx. 100 caracteres | Contraseña actual |
| `newPassword` | string | Sí | 8–100 chars, ≥1 letra, ≥1 número, sin espacios, distinta a la actual | Nueva contraseña |

**Ejemplo de request:**
```json
{
  "currentPassword": "Password123!",
  "newPassword": "NuevaPassword2026!"
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<Void>`:**
```json
{
  "success": true,
  "message": "Contraseña actualizada exitosamente",
  "data": null
}
```

**Error (`400` | `401`) — Envelope `ErrorResponse`:**
```json
{
  "message": "La contraseña actual es incorrecta",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T15:30:00",
  "path": "/api/v1/users/me/password",
  "method": "PATCH"
}
```

---

#### DELETE `/api/v1/users/me`

> Desactiva lógicamente (*soft delete*) la cuenta del usuario autenticado, incrementa `tokenVersion` y revoca todos sus tokens activos.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado  
**Content-Type:** `application/json`

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<Void>`:**
```json
{
  "success": true,
  "message": "Cuenta eliminada exitosamente",
  "data": null
}
```

**Error (`401`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No autorizado: token inválido, expirado o ausente",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-11-18T15:30:00",
  "path": "/api/v1/users/me",
  "method": null
}
```

---

### 📍 Direcciones de Entrega (`CustomerAddressController`)

#### GET `/api/v1/users/me/addresses`

> Lista todas las direcciones de entrega guardadas del usuario autenticado.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado  
**Content-Type:** `application/json`

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<List<CustomerAddressResponse>>`:**
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
      "createdAt": "2026-11-18T14:00:00Z",
      "updatedAt": "2026-11-18T14:00:00Z"
    }
  ]
}
```

**Error (`401`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No autorizado: token inválido, expirado o ausente",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/users/me/addresses",
  "method": null
}
```

---

#### POST `/api/v1/users/me/addresses`

> Registra una nueva dirección de entrega con coordenadas geográficas WGS84. Si `title` se omite (`null`), se asigna `"Casa"` por defecto.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON) — `CustomerAddressRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `title` | string | No | Máx. 50 caracteres (default `"Casa"`) | Etiqueta identificadora |
| `addressLine` | string | Sí | No vacío, máx. 255 caracteres | Dirección completa legible |
| `reference` | string | No | Máx. 500 caracteres | Referencia para el repartidor |
| `latitude` | Double | Sí | `-90.0` a `90.0` | Latitud WGS84 |
| `longitude` | Double | Sí | `-180.0` a `180.0` | Longitud WGS84 |

**Ejemplo de request:**
```json
{
  "title": "Oficina",
  "addressLine": "Calle Las Begonias 441, San Isidro",
  "reference": "Piso 8, recepción",
  "latitude": -12.093124,
  "longitude": -77.026451
}
```

##### 📤 Response

**Éxito (`201 Created`) — Envelope `ApiResponse<CustomerAddressResponse>`:**
```json
{
  "success": true,
  "message": "Dirección creada exitosamente",
  "data": {
    "id": "d2b3c4d5-e6f7-8901-bcde-fa2345678901",
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "title": "Oficina",
    "addressLine": "Calle Las Begonias 441, San Isidro",
    "reference": "Piso 8, recepción",
    "latitude": -12.093124,
    "longitude": -77.026451,
    "createdAt": "2026-11-18T14:35:00Z",
    "updatedAt": "2026-11-18T14:35:00Z"
  }
}
```

**Error (`400` | `401`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Error de validación: latitude: La latitud debe estar entre -90.0 y 90.0 grados",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T14:35:00",
  "path": "/api/v1/users/me/addresses",
  "method": "POST"
}
```

---

#### PUT `/api/v1/users/me/addresses/{addressId}`

> Actualiza los datos y coordenadas de una dirección perteneciente al usuario autenticado. `title` solo se sobrescribe si viene no vacío.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Propietario de la dirección  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `addressId` | UUID | Sí | ID de la dirección a actualizar |

**Body (JSON):** Mismo formato `CustomerAddressRequest`.

**Ejemplo de request:**
```json
{
  "title": "Oficina Principal",
  "addressLine": "Calle Las Begonias 441, San Isidro",
  "reference": "Torre B, piso 8",
  "latitude": -12.093124,
  "longitude": -77.026451
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<CustomerAddressResponse>`:**
```json
{
  "success": true,
  "message": "Dirección actualizada exitosamente",
  "data": {
    "id": "d2b3c4d5-e6f7-8901-bcde-fa2345678901",
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "title": "Oficina Principal",
    "addressLine": "Calle Las Begonias 441, San Isidro",
    "reference": "Torre B, piso 8",
    "latitude": -12.093124,
    "longitude": -77.026451,
    "createdAt": "2026-11-18T14:35:00Z",
    "updatedAt": "2026-11-18T15:00:00Z"
  }
}
```

**Error (`400` | `401` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Dirección no encontrada",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T15:00:00",
  "path": "/api/v1/users/me/addresses/d2b3c4d5-e6f7-8901-bcde-fa2345678901",
  "method": "PUT"
}
```

---

#### DELETE `/api/v1/users/me/addresses/{addressId}`

> Elimina de forma permanente una dirección guardada del usuario autenticado.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Propietario de la dirección  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `addressId` | UUID | Sí | ID de la dirección a eliminar |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<Void>`:**
```json
{
  "success": true,
  "message": "Dirección eliminada exitosamente",
  "data": null
}
```

**Error (`401` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Dirección no encontrada",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T15:00:00",
  "path": "/api/v1/users/me/addresses/d2b3c4d5-e6f7-8901-bcde-fa2345678901",
  "method": "DELETE"
}
```

---

### 🛡️ Administración Global de Usuarios (`AdminUserController`)

#### GET `/api/v1/admin/users`

> Lista de manera paginada todos los usuarios registrados en la plataforma.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Query Parameters:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `page` | integer | No | Número de página 0-indexed (default: `0`) |
| `size` | integer | No | Elementos por página (default: `20`) |
| `sort` | string | No | Campo y dirección de ordenamiento (ej. `createdAt,desc`) |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<PageResponse<UserResponse>>`:**
```json
{
  "success": true,
  "message": "Usuarios obtenidos exitosamente",
  "data": {
    "content": [
      {
        "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "email": "carlos@ejemplo.com",
        "fullName": "Carlos Rodríguez",
        "phone": "+51999888777",
        "systemRole": "USER",
        "isActive": true,
        "createdAt": "2026-11-18T14:00:00Z",
        "updatedAt": "2026-11-18T14:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

**Error (`401` | `403`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Acceso denegado: no tiene permisos para realizar esta operación",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/admin/users",
  "method": null
}
```

---

#### PATCH `/api/v1/admin/users/{userId}/status`

> Activa o desactiva la cuenta de un usuario global. Al desactivar (`isActive: false`), revoca inmediatamente todos sus refresh tokens.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `userId` | UUID | Sí | ID del usuario |

**Body (JSON) — `UserStatusUpdateRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `isActive` | boolean | Sí | `@NotNull` | `true` para activar, `false` para desactivar |

**Ejemplo de request:**
```json
{
  "isActive": false
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<UserResponse>`:**
```json
{
  "success": true,
  "message": "Usuario desactivado",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "carlos@ejemplo.com",
    "fullName": "Carlos Rodríguez",
    "phone": "+51999888777",
    "systemRole": "USER",
    "isActive": false,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T15:40:00Z"
  }
}
```

*(Si `isActive` es `true`, `message` será `"Usuario activado"`.)*

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Usuario no encontrado",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T15:40:00",
  "path": "/api/v1/admin/users/3fa85f64-5717-4562-b3fc-2c963f66afa6/status",
  "method": "PATCH"
}
```

> **📝 Notas para el Frontend (Identity):**
> - Todos los IDs son **UUID** (strings de 36 caracteres). No intentar parsearlos como números.
> - Las fechas llegan en formato **ISO 8601** (ej: `2026-11-18T14:00:00Z`).
> - `staffMemberships` se incluye en `POST /api/v1/auth/login` y `GET /api/v1/auth/me`, pero viene `null` en `/register` y `/refresh`.
> - Tras un `PATCH /api/v1/users/me/password` o `DELETE /api/v1/users/me`, limpiar el almacenamiento local de tokens y redirigir al login porque todos los tokens quedan invalidados de inmediato.
> - Respetar el header `Retry-After` si se recibe `429 Too Many Requests` en los endpoints de autenticación.

---

## 2. Subscription — Planes SaaS y Suscripciones

Módulo de monetización SaaS que administra los planes comerciales de NexoFood (cacheados en memoria con Caffeine) y el ciclo de vida completo de las suscripciones (`PENDING`, `TRIAL`, `ACTIVE`, `PAST_DUE`, `CANCELED`, `EXPIRED`) incluyendo cobros recurrentes preaprobados con Mercado Pago.

---

### 💳 Planes de Suscripción Públicos (`SubscriptionPlanController`)

#### GET `/api/v1/plans`

> Lista todos los planes de suscripción activos (`isActive = true`) disponibles para contratación.

**Autenticación:** No requerida (Público)  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<List<SubscriptionPlanResponse>>`:**
```json
{
  "success": true,
  "message": "Planes de suscripción obtenidos exitosamente",
  "data": [
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
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
```

**Error (`500`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Ocurrió un error interno en el servidor",
  "error": "INTERNAL_SERVER_ERROR",
  "status": 500,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/plans",
  "method": "GET"
}
```

---

### 🛡️ Administración de Planes (`AdminSubscriptionPlanController`)

#### GET `/api/v1/admin/plans`

> Lista todos los planes registrados en la plataforma, incluyendo los inactivos.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<List<SubscriptionPlanResponse>>`:**
```json
{
  "success": true,
  "message": "Todos los planes obtenidos exitosamente",
  "data": [
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
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
```

**Error (`401` | `403`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Acceso denegado: no tiene permisos para realizar esta operación",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/admin/plans",
  "method": null
}
```

---

#### GET `/api/v1/admin/plans/{planId}`

> Obtiene el detalle de un plan de suscripción por su UUID.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `planId` | UUID | Sí | ID del plan de suscripción |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<SubscriptionPlanResponse>`:**
```json
{
  "success": true,
  "message": "Plan obtenido exitosamente",
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Plan Emprendedor",
    "price": 59.90,
    "billingCycleDays": 30,
    "maxProducts": 50,
    "isActive": true,
    "createdAt": "2026-10-22T00:00:00Z",
    "updatedAt": "2026-10-22T00:00:00Z"
  }
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Plan de suscripción no encontrado",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/admin/plans/a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "method": "GET"
}
```

---

#### POST `/api/v1/admin/plans`

> Crea un nuevo plan de suscripción SaaS e invalida la caché de planes. Valida unicidad del nombre (case-insensitive).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON) — `SubscriptionPlanRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `name` | string | Sí | No vacío, máx. 100 caracteres, único | Nombre comercial del plan |
| `price` | BigDecimal | Sí | `0.00` a `999999.99` | Tarifa por ciclo |
| `billingCycleDays` | integer | Sí | `1` a `3650` | Duración del ciclo en días (ej. `30`) |
| `maxProducts` | integer | No | `1` a `1000000` (`null` = ilimitado) | Límite máximo de productos en catálogo |
| `isActive` | boolean | No | Default: `true` | Si el plan es visible públicamente |

**Ejemplo de request:**
```json
{
  "name": "Plan Pro",
  "price": 99.90,
  "billingCycleDays": 30,
  "maxProducts": 150,
  "isActive": true
}
```

##### 📤 Response

**Éxito (`201 Created`) — Envelope `ApiResponse<SubscriptionPlanResponse>`:**
```json
{
  "success": true,
  "message": "Plan de suscripción creado exitosamente",
  "data": {
    "id": "b2b2c3d4-e5f6-7890-abcd-ef1234567891",
    "name": "Plan Pro",
    "price": 99.90,
    "billingCycleDays": 30,
    "maxProducts": 150,
    "isActive": true,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T14:00:00Z"
  }
}
```

**Error (`400` | `401` | `403` | `409`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Ya existe un plan de suscripción con ese nombre",
  "error": "CONFLICT",
  "status": 409,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/admin/plans",
  "method": "POST"
}
```

---

#### PUT `/api/v1/admin/plans/{planId}`

> Actualiza el nombre, precio, duración del ciclo, límite de productos y estado de un plan existente.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `planId` | UUID | Sí | ID del plan a actualizar |

**Body (JSON):** Mismo formato `SubscriptionPlanRequest`.

**Ejemplo de request:**
```json
{
  "name": "Plan Pro Plus",
  "price": 119.90,
  "billingCycleDays": 30,
  "maxProducts": 300,
  "isActive": true
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<SubscriptionPlanResponse>`:**
```json
{
  "success": true,
  "message": "Plan de suscripción actualizado exitosamente",
  "data": {
    "id": "b2b2c3d4-e5f6-7890-abcd-ef1234567891",
    "name": "Plan Pro Plus",
    "price": 119.90,
    "billingCycleDays": 30,
    "maxProducts": 300,
    "isActive": true,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T15:00:00Z"
  }
}
```

**Error (`400` | `401` | `403` | `404` | `409`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Ya existe otro plan de suscripción con ese nombre",
  "error": "CONFLICT",
  "status": 409,
  "timestamp": "2026-11-18T15:00:00",
  "path": "/api/v1/admin/plans/b2b2c3d4-e5f6-7890-abcd-ef1234567891",
  "method": "PUT"
}
```

---

#### PATCH `/api/v1/admin/plans/{planId}/status`

> Activa o desactiva la disponibilidad pública de un plan de suscripción.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `planId` | UUID | Sí | ID del plan |

**Body (JSON) — `SubscriptionPlanStatusRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `isActive` | boolean | Sí | `@NotNull` | `true` para activar, `false` para desactivar |

**Ejemplo de request:**
```json
{
  "isActive": false
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<SubscriptionPlanResponse>`:**
```json
{
  "success": true,
  "message": "Plan desactivado exitosamente",
  "data": {
    "id": "b2b2c3d4-e5f6-7890-abcd-ef1234567891",
    "name": "Plan Pro Plus",
    "price": 119.90,
    "billingCycleDays": 30,
    "maxProducts": 300,
    "isActive": false,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T15:10:00Z"
  }
}
```

*(Si `isActive` es `true`, `message` será `"Plan activado exitosamente"`.)*

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Plan de suscripción no encontrado",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T15:10:00",
  "path": "/api/v1/admin/plans/b2b2c3d4-e5f6-7890-abcd-ef1234567891/status",
  "method": "PATCH"
}
```

---

### 📦 Suscripciones del Usuario (`SubscriptionController`)

#### GET `/api/v1/subscriptions/me`

> Obtiene la suscripción vigente (`TRIAL`, `ACTIVE` o `PENDING`) del usuario autenticado, o su suscripción más reciente como respaldo. Si la suscripción está en `PENDING` con `mpPreapprovalId`, intenta auto-sincronizar con Mercado Pago antes de responder.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado  
**Content-Type:** `application/json`

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<SubscriptionResponse>`:**
```json
{
  "success": true,
  "message": "Suscripción obtenida exitosamente",
  "data": {
    "id": "d1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "plan": {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "name": "Plan Emprendedor",
      "price": 59.90,
      "billingCycleDays": 30,
      "maxProducts": 50,
      "isActive": true,
      "createdAt": "2026-10-22T00:00:00Z",
      "updatedAt": "2026-10-22T00:00:00Z"
    },
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "status": "ACTIVE",
    "startDate": "2026-11-01T10:00:00Z",
    "endDate": "2026-12-01T10:00:00Z",
    "mpPreapprovalId": "2c9380847290abcd017291234567890a",
    "mpPreapprovalStatus": "authorized",
    "cancelAtPeriodEnd": false,
    "createdAt": "2026-11-01T09:55:00Z",
    "updatedAt": "2026-11-01T10:00:05Z"
  }
}
```

**Error (`401` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No se encontró ninguna suscripción para el usuario actual",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/subscriptions/me",
  "method": "GET"
}
```

---

#### GET `/api/v1/subscriptions/me/payments`

> Lista el historial de cobros recurrentes de la suscripción más reciente del usuario autenticado (ordenados de más reciente a más antiguo). Si el usuario no tiene suscripción, retorna una lista vacía `[]`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado  
**Content-Type:** `application/json`

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<List<SubscriptionPaymentResponse>>`:**
```json
{
  "success": true,
  "message": "Historial de cobros de suscripción obtenido exitosamente",
  "data": [
    {
      "id": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "subscriptionId": "d1a2c3d4-e5f6-7890-abcd-ef1234567890",
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

**Error (`401`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No autorizado: token inválido, expirado o ausente",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/subscriptions/me/payments",
  "method": null
}
```

---

#### POST `/api/v1/subscriptions`

> Inicia una nueva suscripción en período de prueba (`TRIAL`) para el usuario autenticado por `billingCycleDays` días.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado sin suscripción `TRIAL`/`ACTIVE` vigente  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON) — `SubscriptionCreateRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `planId` | UUID | Sí | `@NotNull`, plan activo existente | ID del plan SaaS elegido |

**Ejemplo de request:**
```json
{
  "planId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

##### 📤 Response

**Éxito (`201 Created`) — Envelope `ApiResponse<SubscriptionResponse>`:**
```json
{
  "success": true,
  "message": "Suscripción iniciada exitosamente",
  "data": {
    "id": "d1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "plan": {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "name": "Plan Emprendedor",
      "price": 59.90,
      "billingCycleDays": 30,
      "maxProducts": 50,
      "isActive": true,
      "createdAt": "2026-10-22T00:00:00Z",
      "updatedAt": "2026-10-22T00:00:00Z"
    },
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "status": "TRIAL",
    "startDate": "2026-11-18T14:00:00Z",
    "endDate": "2026-12-18T14:00:00Z",
    "mpPreapprovalId": null,
    "mpPreapprovalStatus": null,
    "cancelAtPeriodEnd": false,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T14:00:00Z"
  }
}
```

**Error (`400` | `401` | `404` | `409`) — Envelope `ErrorResponse`:**
```json
{
  "message": "El usuario ya cuenta con una suscripción vigente",
  "error": "CONFLICT",
  "status": 409,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/subscriptions",
  "method": "POST"
}
```

---

#### POST `/api/v1/subscriptions/checkout`

> Genera una suscripción recurrente preaprobada en Mercado Pago (`POST /preapproval`). Devuelve `initPoint` para redirigir al usuario a autorizar el cobro periódico. Si se actualiza desde un `TRIAL` vigente, difiere la fecha del primer débito hasta `endDate`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado (sin suscripción `ACTIVE` y `authorized` vigente)  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON) — `SubscriptionCreateRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `planId` | UUID | Sí | `@NotNull`, plan activo existente | ID del plan SaaS a contratar |

**Ejemplo de request:**
```json
{
  "planId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

##### 📤 Response

**Éxito (`201 Created`) — Envelope `ApiResponse<SubscriptionCheckoutResponse>`:**
```json
{
  "success": true,
  "message": "Enlace de suscripción preaprobada generado exitosamente",
  "data": {
    "subscriptionId": "d1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "planId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "mpPreapprovalId": "2c9380847290abcd017291234567890a",
    "initPoint": "https://www.mercadopago.com.pe/subscriptions/checkout?preapproval_id=2c9380847290abcd017291234567890a",
    "status": "PENDING"
  }
}
```

**Error (`400` | `401` | `404` | `409` | `502`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Ya cuentas con una suscripción activa y autorizada",
  "error": "CONFLICT",
  "status": 409,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/subscriptions/checkout",
  "method": "POST"
}
```

---

#### POST `/api/v1/subscriptions/me/sync`

> Sincroniza manualmente el estado de la suscripción del usuario autenticado consultando directamente el preapproval en Mercado Pago. Útil en la pantalla de retorno del checkout (`/dashboard/subscription/result`) antes de que llegue el webhook.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado  
**Content-Type:** `application/json`

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<SubscriptionResponse>`:**
```json
{
  "success": true,
  "message": "Suscripción sincronizada con Mercado Pago exitosamente",
  "data": {
    "id": "d1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "plan": {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "name": "Plan Emprendedor",
      "price": 59.90,
      "billingCycleDays": 30,
      "maxProducts": 50,
      "isActive": true,
      "createdAt": "2026-10-22T00:00:00Z",
      "updatedAt": "2026-10-22T00:00:00Z"
    },
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "status": "ACTIVE",
    "startDate": "2026-11-18T14:00:00Z",
    "endDate": "2026-12-18T14:00:00Z",
    "mpPreapprovalId": "2c9380847290abcd017291234567890a",
    "mpPreapprovalStatus": "authorized",
    "cancelAtPeriodEnd": false,
    "createdAt": "2026-11-18T13:55:00Z",
    "updatedAt": "2026-11-18T14:02:00Z"
  }
}
```

**Error (`401` | `404` | `502`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No se encontró ninguna suscripción para el usuario",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:02:00",
  "path": "/api/v1/subscriptions/me/sync",
  "method": "POST"
}
```

---

#### PATCH `/api/v1/subscriptions/me/cancel`

> Cancela la suscripción vigente (`TRIAL` o `ACTIVE`) del usuario autenticado.
> - Si es `ACTIVE` con `endDate` en el futuro: cancela la recurrencia en Mercado Pago, mantiene `status = ACTIVE` y marca `cancelAtPeriodEnd = true` hasta el fin del período pagado.
> - Si es `TRIAL` o ya venció: pasa de inmediato a `CANCELED` y **desactiva el restaurante vinculado**.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Usuario con suscripción `TRIAL` o `ACTIVE`  
**Content-Type:** `application/json`

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<SubscriptionResponse>`:**
```json
{
  "success": true,
  "message": "Suscripción cancelada exitosamente",
  "data": {
    "id": "d1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "plan": {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "name": "Plan Emprendedor",
      "price": 59.90,
      "billingCycleDays": 30,
      "maxProducts": 50,
      "isActive": true,
      "createdAt": "2026-10-22T00:00:00Z",
      "updatedAt": "2026-10-22T00:00:00Z"
    },
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "status": "ACTIVE",
    "startDate": "2026-11-01T10:00:00Z",
    "endDate": "2026-12-01T10:00:00Z",
    "mpPreapprovalId": "2c9380847290abcd017291234567890a",
    "mpPreapprovalStatus": "cancelled",
    "cancelAtPeriodEnd": true,
    "createdAt": "2026-11-01T09:55:00Z",
    "updatedAt": "2026-11-18T15:00:00Z"
  }
}
```

**Error (`401` | `404` | `502`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No tienes una suscripción activa para cancelar",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T15:00:00",
  "path": "/api/v1/subscriptions/me/cancel",
  "method": "PATCH"
}
```

> **📝 Notas para el Frontend (Subscription):**
> - Todos los IDs son **UUID** (strings de 36 caracteres) y las fechas siguen **ISO 8601**.
> - Antes de crear un restaurante (`POST /api/v1/tenants`), el usuario debe iniciar un trial (`POST /api/v1/subscriptions`) o completar un checkout (`POST /api/v1/subscriptions/checkout`).
> - Al regresar de Mercado Pago a `/dashboard/subscription/result`, llamar a `POST /api/v1/subscriptions/me/sync` para confirmar la activación sin depender del retraso del webhook.
> - Si `cancelAtPeriodEnd === true` con `status === "ACTIVE"`, mostrar un aviso indicando hasta qué fecha (`endDate`) permanecerá activo el restaurante.

---

## 3. Tenant / Store — Restaurantes, Staff, Invitaciones, Clientes y Storefront Auth

> **🏪 Multi-Tenant:** Este módulo administra cada restaurante (`Tenant`) con sus horarios de atención (`openingTime`, `closingTime`, `isOpen`), el listado global para `SUPERADMIN` (`AdminTenantController`), el equipo operativo (`TenantMemberController`), las invitaciones por correo (`TenantInvitationController`), sus clientes fidelizados (`TenantCustomerController`) y la autenticación contextual del storefront (`StoreAuthController`). El `tenantId` se obtiene desde `GET /api/v1/tenants/me` o desde `staffMemberships` del login.

---

### 🏪 Gestión de Restaurantes (`TenantController`)

#### GET `/api/v1/tenants/me`

> Obtiene el restaurante asociado al usuario autenticado como propietario (`owner`) o, en su defecto, el primer restaurante donde es miembro activo de staff. Si no tiene tienda asociada, responde `404 Not Found` para disparar el onboarding.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado  
**Content-Type:** `application/json`

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<TenantResponse>`:**
```json
{
  "success": true,
  "message": "Restaurante del usuario obtenido exitosamente",
  "data": {
    "id": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "subscriptionId": "d1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "ownerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "Burger Bros",
    "slug": "burger-bros",
    "logoUrl": "https://storage.nexofood.lat/logos/burger.png",
    "bannerUrl": "https://storage.nexofood.lat/banners/burger.png",
    "phone": "+51987654321",
    "address": "Av. Larco 456, Miraflores",
    "latitude": -12.121543,
    "longitude": -77.028941,
    "deliveryRadiusKm": 6.50,
    "defaultDeliveryFee": 5.00,
    "isMpConnected": false,
    "onlinePaymentsEnabled": false,
    "mpUserId": null,
    "mpConnectedAt": null,
    "mpTokenExpiresAt": null,
    "isActive": true,
    "openingTime": "09:00:00",
    "closingTime": "23:00:00",
    "isOpen": true,
    "createdAt": "2026-11-22T14:00:00Z",
    "updatedAt": "2026-11-22T14:00:00Z"
  }
}
```

**Error (`401` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No tienes ninguna tienda asociada",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-22T14:00:00",
  "path": "/api/v1/tenants/me",
  "method": "GET"
}
```

---

#### GET `/api/v1/tenants/{id}`

> Obtiene el detalle completo de un restaurante por su UUID.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante (`OWNER`, `ADMIN`, `COCINERO`, `REPARTIDOR`) o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `id` | UUID | Sí | ID del restaurante |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<TenantResponse>`:**
```json
{
  "success": true,
  "message": "Restaurante obtenido exitosamente",
  "data": {
    "id": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "subscriptionId": "d1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "ownerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "Burger Bros",
    "slug": "burger-bros",
    "logoUrl": "https://storage.nexofood.lat/logos/burger.png",
    "bannerUrl": "https://storage.nexofood.lat/banners/burger.png",
    "phone": "+51987654321",
    "address": "Av. Larco 456, Miraflores",
    "latitude": -12.121543,
    "longitude": -77.028941,
    "deliveryRadiusKm": 6.50,
    "defaultDeliveryFee": 5.00,
    "isMpConnected": true,
    "onlinePaymentsEnabled": true,
    "mpUserId": "123456789",
    "mpConnectedAt": "2026-11-10T10:00:00Z",
    "mpTokenExpiresAt": "2027-05-09T10:00:00Z",
    "isActive": true,
    "openingTime": "09:00:00",
    "closingTime": "23:00:00",
    "isOpen": true,
    "createdAt": "2026-11-22T14:00:00Z",
    "updatedAt": "2026-11-22T14:00:00Z"
  }
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Restaurante no encontrado",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-22T14:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890",
  "method": "GET"
}
```

---

#### GET `/api/v1/tenants/slug/{slug}`

> Obtiene la información pública de un restaurante mediante su `slug` único (usado por el storefront para cargar cabecera, ubicación, radio de entrega, horario, estado abierto/cerrado `isOpen` y disponibilidad de pagos online).

**Autenticación:** No requerida (Público)  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `slug` | string | Sí | Slug público del restaurante (ej. `burger-bros`) |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<TenantResponse>`:**
```json
{
  "success": true,
  "message": "Restaurante obtenido exitosamente",
  "data": {
    "id": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "subscriptionId": "d1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "ownerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "Burger Bros",
    "slug": "burger-bros",
    "logoUrl": "https://storage.nexofood.lat/logos/burger.png",
    "bannerUrl": "https://storage.nexofood.lat/banners/burger.png",
    "phone": "+51987654321",
    "address": "Av. Larco 456, Miraflores",
    "latitude": -12.121543,
    "longitude": -77.028941,
    "deliveryRadiusKm": 6.50,
    "defaultDeliveryFee": 5.00,
    "isMpConnected": true,
    "onlinePaymentsEnabled": true,
    "mpUserId": "123456789",
    "mpConnectedAt": "2026-11-10T10:00:00Z",
    "mpTokenExpiresAt": "2027-05-09T10:00:00Z",
    "isActive": true,
    "openingTime": "09:00:00",
    "closingTime": "23:00:00",
    "isOpen": true,
    "createdAt": "2026-11-22T14:00:00Z",
    "updatedAt": "2026-11-22T14:00:00Z"
  }
}
```

**Error (`404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Restaurante no encontrado con slug: burger-bros",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-22T14:00:00",
  "path": "/api/v1/tenants/slug/burger-bros",
  "method": "GET"
}
```

---

#### POST `/api/v1/tenants`

> Registra un nuevo restaurante asignando como propietario (`OWNER`) al usuario autenticado y creando automáticamente su membresía de staff. Si se omite `subscriptionId`, vincula automáticamente la suscripción vigente (`TRIAL` o `ACTIVE`) del usuario. Si se configuran `openingTime` y `closingTime`, el estado inicial `isOpen` se calcula automáticamente según la hora actual (incluyendo horarios nocturnos como `18:00:00`–`02:00:00` o `24h` cuando `openingTime == closingTime`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado con suscripción vigente (`TRIAL` o `ACTIVE`) y sin restaurante propio  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON) — `TenantCreateRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `subscriptionId` | UUID | No | UUID válido (propiedad del usuario) | ID de suscripción (se auto-resuelve si se omite) |
| `name` | string | Sí | No vacío, máx. 150 caracteres | Nombre comercial |
| `slug` | string | Sí | `^[a-z0-9-]+$`, máx. 100 caracteres, único | Slug de la URL del storefront |
| `logoUrl` | string | No | Máx. 500 caracteres | URL del logotipo |
| `bannerUrl` | string | No | Máx. 500 caracteres | URL del banner |
| `phone` | string | No | Máx. 20 caracteres | Teléfono del restaurante |
| `address` | string | No | Máx. 255 caracteres | Dirección física del local |
| `latitude` | Double | No | `-90.0` a `90.0` | Latitud del local (WGS84) |
| `longitude` | Double | No | `-180.0` a `180.0` | Longitud del local (WGS84) |
| `deliveryRadiusKm` | BigDecimal | No | `0.00` a `100.00` (default: `5.00`) | Radio máximo de cobertura en km |
| `defaultDeliveryFee` | BigDecimal | No | `0.00` a `1000.00` (default: `0.00`) | Costo estándar de delivery |
| `openingTime` | string (`LocalTime`) | No | Formato ISO `HH:mm:ss` o `HH:mm` | Hora de apertura del restaurante |
| `closingTime` | string (`LocalTime`) | No | Formato ISO `HH:mm:ss` o `HH:mm` | Hora de cierre del restaurante (puede ser menor a `openingTime` para horario nocturno) |

**Ejemplo de request:**
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
  "deliveryRadiusKm": 6.50,
  "defaultDeliveryFee": 5.00,
  "openingTime": "09:00:00",
  "closingTime": "23:00:00"
}
```

##### 📤 Response

**Éxito (`201 Created`) — Envelope `ApiResponse<TenantResponse>`:**
```json
{
  "success": true,
  "message": "Restaurante creado exitosamente",
  "data": {
    "id": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "subscriptionId": "d1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "ownerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "Burger Bros",
    "slug": "burger-bros",
    "logoUrl": "https://storage.nexofood.lat/logos/burger.png",
    "bannerUrl": "https://storage.nexofood.lat/banners/burger.png",
    "phone": "+51987654321",
    "address": "Av. Larco 456, Miraflores",
    "latitude": -12.121543,
    "longitude": -77.028941,
    "deliveryRadiusKm": 6.50,
    "defaultDeliveryFee": 5.00,
    "isMpConnected": false,
    "onlinePaymentsEnabled": false,
    "mpUserId": null,
    "mpConnectedAt": null,
    "mpTokenExpiresAt": null,
    "isActive": true,
    "openingTime": "09:00:00",
    "closingTime": "23:00:00",
    "isOpen": true,
    "createdAt": "2026-11-22T14:00:00Z",
    "updatedAt": "2026-11-22T14:00:00Z"
  }
}
```

**Error (`400` | `401` | `403` | `409`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No cuentas con una suscripción vigente para crear un restaurante",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-22T14:00:00",
  "path": "/api/v1/tenants",
  "method": "POST"
}
```

---

#### PUT `/api/v1/tenants/{id}`

> Actualiza los datos comerciales, horarios y estado abierto/cerrado del restaurante. Solo sobrescribe los campos enviados no nulos (las coordenadas solo se actualizan si se envían `latitude` y `longitude` juntos). Si tiene horario configurado (`openingTime` y `closingTime`) y se omite `isOpen`, sincroniza automáticamente `isOpen` con la hora actual (y si está fuera de horario, fuerza `isOpen = false`). Invalida las cachés `storefront_menu` y `storefront_categories`.
>
> **⏰ Automatización (`StoreScheduleScheduler`):** Cada minuto (`app.store.schedule-cron`, default `0 * * * * *`), el backend evalúa las tiendas activas con horario configurado: marca `isOpen = false` automáticamente al pasar `closingTime` (emitiendo `STORE_AUTO_CLOSED` a `OWNER` y `ADMIN`), marca `isOpen = true` al iniciar `openingTime` (emitiendo `STORE_AUTO_OPENED` a `OWNER` y `ADMIN`) y envía avisos preventivos (`STORE_OPENING_SOON` / `STORE_CLOSING_SOON`) con `15` minutos de anticipación por defecto (`app.notifications.store-schedule-warning-minutes`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `id` | UUID | Sí | ID del restaurante |

**Body (JSON) — `TenantUpdateRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `name` | string | Sí | No vacío, máx. 150 caracteres | Nombre comercial |
| `logoUrl` | string | No | Máx. 500 caracteres | URL del logo |
| `bannerUrl` | string | No | Máx. 500 caracteres | URL del banner |
| `phone` | string | No | Máx. 20 caracteres | Teléfono |
| `address` | string | No | Máx. 255 caracteres | Dirección |
| `latitude` | Double | No | `-90.0` a `90.0` | Latitud |
| `longitude` | Double | No | `-180.0` a `180.0` | Longitud |
| `deliveryRadiusKm` | BigDecimal | No | `0.00` a `100.00` | Radio de cobertura (km) |
| `defaultDeliveryFee` | BigDecimal | No | `0.00` a `1000.00` | Tarifa estándar de envío |
| `isActive` | boolean | No | — | Estado operativo |
| `openingTime` | string (`LocalTime`) | No | Formato ISO `HH:mm:ss` o `HH:mm` | Hora de apertura |
| `closingTime` | string (`LocalTime`) | No | Formato ISO `HH:mm:ss` o `HH:mm` | Hora de cierre |
| `isOpen` | boolean | No | — | Estado abierto/cerrado manual (si hay horario y está fuera de rango, el servidor fuerza `false`) |

**Ejemplo de request:**
```json
{
  "name": "Burger Bros Gourmet",
  "deliveryRadiusKm": 7.00,
  "defaultDeliveryFee": 6.00,
  "openingTime": "09:00:00",
  "closingTime": "23:00:00"
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<TenantResponse>`:**
```json
{
  "success": true,
  "message": "Restaurante actualizado exitosamente",
  "data": {
    "id": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "subscriptionId": "d1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "ownerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "Burger Bros Gourmet",
    "slug": "burger-bros",
    "logoUrl": "https://storage.nexofood.lat/logos/burger.png",
    "bannerUrl": "https://storage.nexofood.lat/banners/burger.png",
    "phone": "+51987654321",
    "address": "Av. Larco 456, Miraflores",
    "latitude": -12.121543,
    "longitude": -77.028941,
    "deliveryRadiusKm": 7.00,
    "defaultDeliveryFee": 6.00,
    "isMpConnected": false,
    "onlinePaymentsEnabled": false,
    "mpUserId": null,
    "mpConnectedAt": null,
    "mpTokenExpiresAt": null,
    "isActive": true,
    "openingTime": "09:00:00",
    "closingTime": "23:00:00",
    "isOpen": true,
    "createdAt": "2026-11-22T14:00:00Z",
    "updatedAt": "2026-11-22T15:00:00Z"
  }
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Acceso denegado: no tiene permisos para realizar esta operación",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-22T15:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890",
  "method": null
}
```

---

#### PATCH `/api/v1/tenants/{id}/status`

> Activa o desactiva las operaciones del restaurante e invalida la caché del storefront.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `id` | UUID | Sí | ID del restaurante |

**Body (JSON) — `TenantStatusRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `isActive` | boolean | Sí | `@NotNull` | `true` para activar, `false` para desactivar |

**Ejemplo de request:**
```json
{
  "isActive": false
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<Void>`:**
```json
{
  "success": true,
  "message": "Estado del restaurante actualizado",
  "data": null
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Restaurante no encontrado",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-22T15:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/status",
  "method": "PATCH"
}
```

---

### 🛡️ Administración Global de Restaurantes (`AdminTenantController`)

#### GET `/api/v1/admin/tenants` *(alias: `/api/v1/admin/stores`)*

> Lista de forma paginada todos los restaurantes registrados en la plataforma para supervisión global de `SUPERADMIN`. Permite filtrar por estado activo (`isActive`), estado efectivo abierto/cerrado (`isOpen`, considerando horarios configurados) y búsqueda parcial insensible a mayúsculas en `name` o `slug`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Query Parameters:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `isActive` | boolean | No | Filtra por tiendas activas (`true`) o inactivas (`false`) |
| `isOpen` | boolean | No | Filtra por estado efectivo abierto (`true`) o cerrado (`false`) evaluando `isActive`, `openingTime`/`closingTime` (o `isOpen` si no hay horario) |
| `search` | string | No | Coincidencia parcial case-insensitive en `name` o `slug` |
| `page` | integer | No | Número de página 0-indexed (default: `0`) |
| `size` | integer | No | Elementos por página (default: `20`) |
| `sort` | string | No | Ordenamiento (ej. `createdAt,desc` o `name,asc`) |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<PageResponse<TenantResponse>>`:**
```json
{
  "success": true,
  "message": "Tiendas obtenidas exitosamente",
  "data": {
    "content": [
      {
        "id": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
        "subscriptionId": "d1a2c3d4-e5f6-7890-abcd-ef1234567890",
        "ownerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "name": "Burger Bros",
        "slug": "burger-bros",
        "logoUrl": "https://storage.nexofood.lat/logos/burger.png",
        "bannerUrl": "https://storage.nexofood.lat/banners/burger.png",
        "phone": "+51987654321",
        "address": "Av. Larco 456, Miraflores",
        "latitude": -12.121543,
        "longitude": -77.028941,
        "deliveryRadiusKm": 6.50,
        "defaultDeliveryFee": 5.00,
        "isMpConnected": true,
        "onlinePaymentsEnabled": true,
        "mpUserId": "123456789",
        "mpConnectedAt": "2026-11-10T10:00:00Z",
        "mpTokenExpiresAt": "2027-05-09T10:00:00Z",
        "isActive": true,
        "openingTime": "09:00:00",
        "closingTime": "23:00:00",
        "isOpen": true,
        "createdAt": "2026-11-22T14:00:00Z",
        "updatedAt": "2026-11-22T14:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

**Error (`401` | `403`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Acceso denegado: no tiene permisos para realizar esta operación",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-22T14:00:00",
  "path": "/api/v1/admin/tenants",
  "method": null
}
```

---

### 👥 Equipo / Staff (`TenantMemberController`)

> **🏪 Multi-Tenant:** Estos endpoints operan en el contexto de un restaurante específico (`tenantId`). Solo `OWNER` (o `SUPERADMIN`) puede invitar, modificar o desvincular administradores (`ADMIN`). Nadie puede modificar ni desvincular al `OWNER`. Invitar a un miembro crea una invitación pendiente (`PENDING`) por correo electrónico y envía una notificación `STAFF_INVITATION`; la membresía `TenantMember` se crea únicamente cuando el usuario invitado acepta la invitación.

#### GET `/api/v1/tenants/{tenantId}/members`

> Lista todos los miembros del equipo (activos e inactivos) del restaurante.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `tenantId` | UUID | Sí | ID del restaurante |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<List<TenantMemberResponse>>`:**
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
      "createdAt": "2026-11-22T14:00:00Z",
      "updatedAt": "2026-11-22T14:00:00Z",
      "invitationStatus": null
    }
  ]
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Restaurante no encontrado",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-22T14:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/members",
  "method": "GET"
}
```

---

#### GET `/api/v1/tenants/{tenantId}/members/invitations`

> Lista las invitaciones enviadas por el restaurante, con filtro opcional por estado (`PENDING`, `ACCEPTED`, `REJECTED`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `tenantId` | UUID | Sí | ID del restaurante |

**Query Parameters:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `status` | string (`TenantInvitationStatus`) | No | Filtra por `PENDING`, `ACCEPTED` o `REJECTED` |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<List<TenantMemberResponse>>`:**
```json
{
  "success": true,
  "message": "Invitaciones obtenidas exitosamente",
  "data": [
    {
      "id": "a2b3c4d5-e6f7-8901-abcd-ef1234567890",
      "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "userId": "4fa85f64-5717-4562-b3fc-2c963f66afa7",
      "userFullName": "Ana Gómez",
      "userEmail": "ana@ejemplo.com",
      "role": "COCINERO",
      "isActive": false,
      "createdAt": "2026-11-22T14:30:00Z",
      "updatedAt": "2026-11-22T14:30:00Z",
      "invitationStatus": "PENDING"
    }
  ]
}
```

**Error (`400` | `401` | `403`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Acceso denegado: no tiene permisos para realizar esta operación",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-22T14:30:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/members/invitations",
  "method": null
}
```

---

#### POST `/api/v1/tenants/{tenantId}/members`

> Envía una invitación a un usuario global registrado buscándolo por su correo electrónico (`email`, cualquier correo válido sin restricción de dominio/proveedor) para unirse al staff con un rol operativo. Crea una `TenantInvitation` en estado `PENDING` **sin** agregar todavía al `TenantMember` y genera una notificación accionable `STAFF_INVITATION` para que el usuario invitado responda `ACCEPT` o `REJECT`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` (solo para `COCINERO`/`REPARTIDOR`) o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `tenantId` | UUID | Sí | ID del restaurante |

**Body (JSON) — `TenantMemberRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `email` | string | Sí | `@NotBlank("El correo electrónico del usuario es obligatorio")`, `@Email("Debe proporcionar un correo electrónico válido")` | Correo electrónico del usuario registrado a invitar |
| `role` | string | Sí | `@NotNull`, `OWNER` *(solo SUPERADMIN)*, `ADMIN` *(solo OWNER/SUPERADMIN)*, `COCINERO`, `REPARTIDOR` | Rol ofrecido en el restaurante |

**Ejemplo de request:**
```json
{
  "email": "ana@ejemplo.com",
  "role": "COCINERO"
}
```

##### 📤 Response

**Éxito (`201 Created`) — Envelope `ApiResponse<TenantMemberResponse>`:**
```json
{
  "success": true,
  "message": "Invitación enviada exitosamente",
  "data": {
    "id": "a2b3c4d5-e6f7-8901-abcd-ef1234567890",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "userId": "4fa85f64-5717-4562-b3fc-2c963f66afa7",
    "userFullName": "Ana Gómez",
    "userEmail": "ana@ejemplo.com",
    "role": "COCINERO",
    "isActive": false,
    "createdAt": "2026-11-22T14:30:00Z",
    "updatedAt": "2026-11-22T14:30:00Z",
    "invitationStatus": "PENDING"
  }
}
```

**Error (`400` | `401` | `403` | `404` | `409`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Ya existe una invitación pendiente para este usuario en este restaurante",
  "error": "CONFLICT",
  "status": 409,
  "timestamp": "2026-11-22T14:30:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/members",
  "method": "POST"
}
```

---

#### PATCH `/api/v1/tenants/{tenantId}/members/{memberId}/role`

> Cambia el rol operativo de un empleado del restaurante.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` (sin gestionar otros `ADMIN`/`OWNER`) o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `tenantId` | UUID | Sí | ID del restaurante |
| `memberId` | UUID | Sí | ID del registro `TenantMember` |

**Body (JSON) — `MemberRoleUpdateRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `role` | string | Sí | `ADMIN`, `COCINERO`, `REPARTIDOR` (`OWNER` solo `SUPERADMIN`) | Nuevo rol |

**Ejemplo de request:**
```json
{
  "role": "ADMIN"
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<TenantMemberResponse>`:**
```json
{
  "success": true,
  "message": "Rol actualizado exitosamente",
  "data": {
    "id": "f2a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "userId": "4fa85f64-5717-4562-b3fc-2c963f66afa7",
    "userFullName": "Ana Gómez",
    "userEmail": "ana@ejemplo.com",
    "role": "ADMIN",
    "isActive": true,
    "createdAt": "2026-11-22T14:30:00Z",
    "updatedAt": "2026-11-22T15:00:00Z",
    "invitationStatus": null
  }
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No se puede modificar el rol del propietario (OWNER) del restaurante",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-22T15:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/members/f1a2c3d4-e5f6-7890-abcd-ef1234567890/role",
  "method": "PATCH"
}
```

---

#### DELETE `/api/v1/tenants/{tenantId}/members/{memberId}`

> Desvincula permanentemente a un miembro del staff del restaurante.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` (solo para desvincular `COCINERO`/`REPARTIDOR`) o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `tenantId` | UUID | Sí | ID del restaurante |
| `memberId` | UUID | Sí | ID del registro `TenantMember` |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<Void>`:**
```json
{
  "success": true,
  "message": "Miembro desvinculado exitosamente",
  "data": null
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "El propietario (OWNER) no puede ser desvinculado del restaurante",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-22T15:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/members/f1a2c3d4-e5f6-7890-abcd-ef1234567890",
  "method": "DELETE"
}
```

---

### ✉️ Invitaciones del Usuario Autenticado (`TenantInvitationController`)

> Permite al usuario autenticado consultar sus invitaciones pendientes de staff entre múltiples restaurantes y aceptarlas o rechazarlas directamente por `invitationId` (alternativa equivalente a responder desde `POST /api/v1/notifications/{id}/respond`; ambos flujos sincronizan el estado de la invitación y la notificación).

#### GET `/api/v1/invitations`

> Lista las invitaciones en estado `PENDING` recibidas por el usuario autenticado, ordenadas de más reciente a más antigua.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado  
**Content-Type:** `application/json`

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<List<TenantMemberResponse>>`:**
```json
{
  "success": true,
  "message": "Invitaciones obtenidas exitosamente",
  "data": [
    {
      "id": "a2b3c4d5-e6f7-8901-abcd-ef1234567890",
      "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "userId": "4fa85f64-5717-4562-b3fc-2c963f66afa7",
      "userFullName": "Ana Gómez",
      "userEmail": "ana@ejemplo.com",
      "role": "COCINERO",
      "isActive": false,
      "createdAt": "2026-11-22T14:30:00Z",
      "updatedAt": "2026-11-22T14:30:00Z",
      "invitationStatus": "PENDING"
    }
  ]
}
```

**Error (`401`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No autorizado: token inválido, expirado o ausente",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-11-22T14:30:00",
  "path": "/api/v1/invitations",
  "method": null
}
```

---

#### POST `/api/v1/invitations/{invitationId}/respond` *(alias: `PATCH /api/v1/invitations/{invitationId}/respond`)*

> Acepta o rechaza una invitación pendiente del usuario autenticado usando únicamente `"ACCEPT"` o `"REJECT"`.
> - Si `decision == "ACCEPT"`: marca la invitación como `ACCEPTED`, registra al usuario como `TenantMember` activo (`isActive = true`), sincroniza la notificación `STAFF_INVITATION` asociada y envía una notificación `INVITATION_ACCEPTED` al propietario (`OWNER`) del restaurante.
> - Si `decision == "REJECT"`: marca la invitación como `REJECTED` sin crear `TenantMember`, sincroniza la notificación `STAFF_INVITATION` asociada y envía una notificación `INVITATION_REJECTED` al propietario (`OWNER`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Usuario destinatario de la invitación  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `invitationId` | UUID | Sí | ID de la invitación (`TenantInvitation.id`) |

**Body (JSON) — `NotificationInvitationDecisionRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `decision` | string (`InvitationActionType`) | Sí | `@NotNull`, únicamente `"ACCEPT"` o `"REJECT"` | Decisión sobre la invitación |

**Ejemplo de request:**
```json
{
  "decision": "ACCEPT"
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<TenantMemberResponse>`:**
```json
{
  "success": true,
  "message": "Invitación aceptada exitosamente",
  "data": {
    "id": "a2b3c4d5-e6f7-8901-abcd-ef1234567890",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "userId": "4fa85f64-5717-4562-b3fc-2c963f66afa7",
    "userFullName": "Ana Gómez",
    "userEmail": "ana@ejemplo.com",
    "role": "COCINERO",
    "isActive": true,
    "createdAt": "2026-11-22T14:30:00Z",
    "updatedAt": "2026-11-22T14:35:00Z",
    "invitationStatus": "ACCEPTED"
  }
}
```

*(Cuando `decision` es `"REJECT"`, `message` será `"Invitación rechazada exitosamente"`, `isActive` será `false` y `invitationStatus` será `"REJECTED"`.)*

**Error (`400` | `401` | `403` | `404` | `409`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Esta invitación ya fue respondida",
  "error": "CONFLICT",
  "status": 409,
  "timestamp": "2026-11-22T14:36:00",
  "path": "/api/v1/invitations/a2b3c4d5-e6f7-8901-abcd-ef1234567890/respond",
  "method": "POST"
}
```

---

### 🛍️ Clientes de Tienda — Panel Admin (`TenantCustomerController`)

> **🏪 Multi-Tenant:** Estos endpoints administran la ficha de los comensales (`TenantCustomer`) de un restaurante específico. El parámetro `customerId` en la ruta corresponde al `userId` global del cliente.

#### GET `/api/v1/admin/tenants/{tenantId}/customers`

> Lista paginada de clientes asociados al restaurante. **Nota:** Retorna `org.springframework.data.domain.Page<TenantCustomerResponse>` dentro de `data`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `tenantId` | UUID | Sí | ID del restaurante |

**Query Parameters:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `page` | integer | No | Número de página 0-indexed (default: `0`) |
| `size` | integer | No | Tamaño de página (default: `20`) |
| `sort` | string | No | Ordenamiento (ej. `createdAt,desc`) |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<Page<TenantCustomerResponse>>`:**
```json
{
  "success": true,
  "message": "Clientes obtenidos exitosamente",
  "data": {
    "content": [
      {
        "id": "5fa85f64-5717-4562-b3fc-2c963f66afa8",
        "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
        "tenantName": "Burger Bros",
        "tenantSlug": "burger-bros",
        "userId": "5fa85f64-5717-4562-b3fc-2c963f66afa8",
        "userFullName": "Lucía Morales",
        "userEmail": "lucia@ejemplo.com",
        "loyaltyPoints": 150,
        "isBlocked": false,
        "notes": "Cliente frecuente",
        "totalOrders": 12,
        "firstOrderAt": "2026-08-10T19:30:00Z",
        "lastOrderAt": "2026-11-15T21:15:00Z",
        "createdAt": "2026-08-10T19:00:00Z",
        "updatedAt": "2026-11-15T21:15:00Z"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "size": 20,
    "number": 0,
    "first": true,
    "last": true
  }
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Restaurante no encontrado",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/admin/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/customers",
  "method": "GET"
}
```

---

#### GET `/api/v1/admin/tenants/{tenantId}/customers/{customerId}`

> Obtiene el detalle de un cliente (puntos de lealtad, historial resumido, bloqueo y notas privadas) en el restaurante.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `tenantId` | UUID | Sí | ID del restaurante |
| `customerId` | UUID | Sí | `userId` del cliente en la tienda |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<TenantCustomerResponse>`:**
```json
{
  "success": true,
  "message": "Cliente obtenido exitosamente",
  "data": {
    "id": "5fa85f64-5717-4562-b3fc-2c963f66afa8",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantName": "Burger Bros",
    "tenantSlug": "burger-bros",
    "userId": "5fa85f64-5717-4562-b3fc-2c963f66afa8",
    "userFullName": "Lucía Morales",
    "userEmail": "lucia@ejemplo.com",
    "loyaltyPoints": 150,
    "isBlocked": false,
    "notes": "Cliente frecuente",
    "totalOrders": 12,
    "firstOrderAt": "2026-08-10T19:30:00Z",
    "lastOrderAt": "2026-11-15T21:15:00Z",
    "createdAt": "2026-08-10T19:00:00Z",
    "updatedAt": "2026-11-15T21:15:00Z"
  }
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Cliente no encontrado en este restaurante",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/admin/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/customers/5fa85f64-5717-4562-b3fc-2c963f66afa8",
  "method": "GET"
}
```

---

#### PATCH `/api/v1/admin/tenants/{tenantId}/customers/{customerId}/block`

> Bloquea o desbloquea a un cliente para impedir que inicie sesión o realice pedidos en este restaurante.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `tenantId` | UUID | Sí | ID del restaurante |
| `customerId` | UUID | Sí | `userId` del cliente |

**Body (JSON) — `CustomerBlockRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `isBlocked` | boolean | Sí | `@NotNull` | `true` para bloquear, `false` para desbloquear |

**Ejemplo de request:**
```json
{
  "isBlocked": true
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<TenantCustomerResponse>`:**
```json
{
  "success": true,
  "message": "Cliente bloqueado",
  "data": {
    "id": "5fa85f64-5717-4562-b3fc-2c963f66afa8",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantName": "Burger Bros",
    "tenantSlug": "burger-bros",
    "userId": "5fa85f64-5717-4562-b3fc-2c963f66afa8",
    "userFullName": "Lucía Morales",
    "userEmail": "lucia@ejemplo.com",
    "loyaltyPoints": 150,
    "isBlocked": true,
    "notes": "Cliente frecuente",
    "totalOrders": 12,
    "firstOrderAt": "2026-08-10T19:30:00Z",
    "lastOrderAt": "2026-11-15T21:15:00Z",
    "createdAt": "2026-08-10T19:00:00Z",
    "updatedAt": "2026-11-18T15:00:00Z"
  }
}
```

*(Si `isBlocked` es `false`, `message` será `"Cliente desbloqueado"`.)*

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Cliente no encontrado en este restaurante",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T15:00:00",
  "path": "/api/v1/admin/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/customers/5fa85f64-5717-4562-b3fc-2c963f66afa8/block",
  "method": "PATCH"
}
```

---

#### PATCH `/api/v1/admin/tenants/{tenantId}/customers/{customerId}/notes`

> Actualiza las notas privadas del staff sobre el cliente. Enviar `notes: null` limpia las notas.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `tenantId` | UUID | Sí | ID del restaurante |
| `customerId` | UUID | Sí | `userId` del cliente |

**Body (JSON) — `CustomerNotesRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `notes` | string | No | Máx. 2000 caracteres | Notas privadas internas del restaurante |

**Ejemplo de request:**
```json
{
  "notes": "Cliente VIP, verificar empaque cuidadoso"
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<TenantCustomerResponse>`:**
```json
{
  "success": true,
  "message": "Notas actualizadas exitosamente",
  "data": {
    "id": "5fa85f64-5717-4562-b3fc-2c963f66afa8",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantName": "Burger Bros",
    "tenantSlug": "burger-bros",
    "userId": "5fa85f64-5717-4562-b3fc-2c963f66afa8",
    "userFullName": "Lucía Morales",
    "userEmail": "lucia@ejemplo.com",
    "loyaltyPoints": 150,
    "isBlocked": false,
    "notes": "Cliente VIP, verificar empaque cuidadoso",
    "totalOrders": 12,
    "firstOrderAt": "2026-08-10T19:30:00Z",
    "lastOrderAt": "2026-11-15T21:15:00Z",
    "createdAt": "2026-08-10T19:00:00Z",
    "updatedAt": "2026-11-18T15:05:00Z"
  }
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Cliente no encontrado en este restaurante",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T15:05:00",
  "path": "/api/v1/admin/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/customers/5fa85f64-5717-4562-b3fc-2c963f66afa8/notes",
  "method": "PATCH"
}
```

---

#### PATCH `/api/v1/admin/tenants/{tenantId}/customers/{customerId}/points`

> Ajusta manualmente el saldo de puntos de fidelidad del cliente sumando o restando `adjustment`. El saldo final nunca baja de `0`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `tenantId` | UUID | Sí | ID del restaurante |
| `customerId` | UUID | Sí | `userId` del cliente |

**Body (JSON) — `CustomerPointsAdjustmentRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `adjustment` | integer | Sí | `-100000` a `100000` | Puntos a sumar (positivo) o restar (negativo) |

**Ejemplo de request:**
```json
{
  "adjustment": 50
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<TenantCustomerResponse>`:**
```json
{
  "success": true,
  "message": "Puntos ajustados exitosamente",
  "data": {
    "id": "5fa85f64-5717-4562-b3fc-2c963f66afa8",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantName": "Burger Bros",
    "tenantSlug": "burger-bros",
    "userId": "5fa85f64-5717-4562-b3fc-2c963f66afa8",
    "userFullName": "Lucía Morales",
    "userEmail": "lucia@ejemplo.com",
    "loyaltyPoints": 200,
    "isBlocked": false,
    "notes": "Cliente VIP, verificar empaque cuidadoso",
    "totalOrders": 12,
    "firstOrderAt": "2026-08-10T19:30:00Z",
    "lastOrderAt": "2026-11-15T21:15:00Z",
    "createdAt": "2026-08-10T19:00:00Z",
    "updatedAt": "2026-11-18T15:10:00Z"
  }
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Error de validación: adjustment: El ajuste no puede ser mayor a 100,000",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T15:10:00",
  "path": "/api/v1/admin/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/customers/5fa85f64-5717-4562-b3fc-2c963f66afa8/points",
  "method": "PATCH"
}
```

---

### 🌐 Storefront Auth Contextual (`StoreAuthController`)

> **🏪 Multi-Tenant:** Estos endpoints autentican o registran comensales en el contexto de una tienda específica identificada por `tenantSlug`.

#### POST `/api/v1/store/{tenantSlug}/auth/register`

> Registra una nueva cuenta de usuario global y crea su perfil `TenantCustomer` en la tienda indicada. Requiere que la tienda esté activa; si el email ya existe globalmente, responde `409 Conflict` solicitando iniciar sesión.

**Autenticación:** No requerida *(Público, rate-limited)*  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `tenantSlug` | string | Sí | Slug de la tienda |

**Body (JSON) — `StoreRegisterRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `fullName` | string | Sí | No vacío, máx. 150 caracteres | Nombre completo |
| `email` | string | Sí | Email válido, máx. 255 caracteres | Correo electrónico |
| `password` | string | Sí | 8–100 chars, ≥1 letra, ≥1 número, sin espacios | Contraseña |
| `phone` | string | No | Máx. 20 caracteres | Teléfono de contacto |

**Ejemplo de request:**
```json
{
  "fullName": "Mariana Vega",
  "email": "mariana@ejemplo.com",
  "password": "Password123!",
  "phone": "+51999111222"
}
```

##### 📤 Response

**Éxito (`201 Created`) — Envelope `ApiResponse<StoreAuthResponse>`:**
```json
{
  "success": true,
  "message": "Registro exitoso en la tienda",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "7fa85f64-5717-4562-b3fc-2c963f66afa9",
      "email": "mariana@ejemplo.com",
      "fullName": "Mariana Vega",
      "phone": "+51999111222",
      "systemRole": "USER",
      "isActive": true,
      "createdAt": "2026-11-18T14:00:00Z",
      "updatedAt": "2026-11-18T14:00:00Z"
    },
    "customerProfile": {
      "id": "7fa85f64-5717-4562-b3fc-2c963f66afa9",
      "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "tenantName": "Burger Bros",
      "tenantSlug": "burger-bros",
      "userId": "7fa85f64-5717-4562-b3fc-2c963f66afa9",
      "userFullName": "Mariana Vega",
      "userEmail": "mariana@ejemplo.com",
      "loyaltyPoints": 0,
      "isBlocked": false,
      "notes": null,
      "totalOrders": 0,
      "firstOrderAt": null,
      "lastOrderAt": null,
      "createdAt": "2026-11-18T14:00:00Z",
      "updatedAt": "2026-11-18T14:00:00Z"
    }
  }
}
```

**Error (`400` | `404` | `409` | `429`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Ya existe una cuenta con este email. Por favor, inicia sesión en la tienda.",
  "error": "CONFLICT",
  "status": 409,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/store/burger-bros/auth/register",
  "method": "POST"
}
```

---

#### POST `/api/v1/store/{tenantSlug}/auth/login`

> Autentica con credenciales globales y retorna los tokens JWT junto con el `customerProfile` en la tienda. Si el usuario nunca había visitado esta tienda, crea automáticamente su ficha `TenantCustomer`. Rechaza con `401 Unauthorized` si está bloqueado en esta tienda.

**Autenticación:** No requerida *(Público, rate-limited)*  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `tenantSlug` | string | Sí | Slug de la tienda |

**Body (JSON) — `StoreLoginRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `email` | string | Sí | Email válido, máx. 255 caracteres | Correo electrónico |
| `password` | string | Sí | No vacío, máx. 100 caracteres | Contraseña |

**Ejemplo de request:**
```json
{
  "email": "mariana@ejemplo.com",
  "password": "Password123!"
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<StoreAuthResponse>`:**
```json
{
  "success": true,
  "message": "Inicio de sesión exitoso en la tienda",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "7fa85f64-5717-4562-b3fc-2c963f66afa9",
      "email": "mariana@ejemplo.com",
      "fullName": "Mariana Vega",
      "phone": "+51999111222",
      "systemRole": "USER",
      "isActive": true,
      "createdAt": "2026-11-18T14:00:00Z",
      "updatedAt": "2026-11-18T14:00:00Z"
    },
    "customerProfile": {
      "id": "7fa85f64-5717-4562-b3fc-2c963f66afa9",
      "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "tenantName": "Burger Bros",
      "tenantSlug": "burger-bros",
      "userId": "7fa85f64-5717-4562-b3fc-2c963f66afa9",
      "userFullName": "Mariana Vega",
      "userEmail": "mariana@ejemplo.com",
      "loyaltyPoints": 0,
      "isBlocked": false,
      "notes": null,
      "totalOrders": 0,
      "firstOrderAt": null,
      "lastOrderAt": null,
      "createdAt": "2026-11-18T14:00:00Z",
      "updatedAt": "2026-11-18T14:00:00Z"
    }
  }
}
```

**Error (`400` | `401` | `404` | `429`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Tu cuenta ha sido bloqueada por este comercio. Por favor contacta al restaurante para más información.",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/store/burger-bros/auth/login",
  "method": "POST"
}
```

> **📝 Notas para el Frontend (Tenant / Store):**
> - Todos los IDs son **UUID** y las fechas llegan en **ISO 8601**. Los horarios de tienda (`openingTime`, `closingTime`) usan `LocalTime` en formato `HH:mm:ss` (ej. `"09:00:00"`).
> - `GET /api/v1/tenants/me` devuelve `404` cuando un usuario recién registrado aún no crea su restaurante; capturar ese `404` para dirigirlo al flujo de onboarding.
> - En `GET /api/v1/admin/tenants/{tenantId}/customers`, el objeto `data` usa el formato `Page` de Spring Data (`number`, `size`, `totalElements`, `totalPages`, `content`, `first`, `last`), a diferencia de los demás endpoints paginados que usan `PageResponse` (`page` en lugar de `number`).
> - En `GET /api/v1/admin/tenants` (o `/api/v1/admin/stores`), sí se utiliza `PageResponse<TenantResponse>`.
> - Al consumir `GET /api/v1/tenants/slug/{slug}`, verificar tanto `isActive` como `isOpen` (y mostrar `openingTime`–`closingTime`) antes de permitir compras en el storefront.
> - Para invitar personal al restaurante (`POST /api/v1/tenants/{tenantId}/members`), enviar el `email` del usuario registrado; el endpoint devuelve la invitación con `isActive: false` e `invitationStatus: "PENDING"` hasta que el invitado responda `ACCEPT`.

---

## 4. Catalog — Impuestos, Categorías, Productos, Reglas de Precio y Menú Público

> **🏪 Multi-Tenant:** Este módulo opera en el contexto de un restaurante específico (`tenantId` en backoffice, `slug` en storefront).
> - **Lecturas de Backoffice (`GET /api/v1/tenants/{tenantId}/**`):** Accesibles para cualquier miembro activo del staff (`OWNER`, `ADMIN`, `COCINERO`, `REPARTIDOR`) y `SUPERADMIN`.
> - **Mutaciones de Backoffice (`POST`, `PUT`, `PATCH`, `DELETE`):** Restringidas a `OWNER`, `ADMIN` y `SUPERADMIN`.
> - **Storefront Público (`/api/v1/store/{slug}/menu`, `/categories`, `/products`, `/products/{id}`):** Públicos sin JWT, con caché en memoria (Caffeine) invalidada automáticamente al editar el catálogo.

---

### 🏛️ Gestión de Impuestos (`TaxController`)

#### GET `/api/v1/tenants/{tenantId}/taxes`

> Lista todos los impuestos configurados en el restaurante.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante (`OWNER`, `ADMIN`, `COCINERO`, `REPARTIDOR`) o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `tenantId` | UUID | Sí | ID del restaurante |

**Query Parameters:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `activeOnly` | boolean | No | Si es `true`, filtra solo impuestos activos (default: `false`) |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<List<TaxResponse>>`:**
```json
{
  "success": true,
  "message": "Impuestos obtenidos exitosamente",
  "data": [
    {
      "id": "11111111-2222-3333-4444-555555555555",
      "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "name": "IGV 18%",
      "rate": 18.00,
      "code": "1000",
      "isInclusive": true,
      "isActive": true,
      "createdAt": "2026-11-18T14:00:00Z",
      "updatedAt": "2026-11-18T14:00:00Z"
    }
  ]
}
```

**Error (`401` | `403`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Acceso denegado: no tiene permisos para realizar esta operación",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/taxes",
  "method": null
}
```

---

#### GET `/api/v1/tenants/{tenantId}/taxes/{taxId}`

> Obtiene el detalle de un impuesto del restaurante por su ID.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `tenantId` | UUID | Sí | ID del restaurante |
| `taxId` | UUID | Sí | ID del impuesto |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<TaxResponse>`:**
```json
{
  "success": true,
  "message": "Impuesto obtenido exitosamente",
  "data": {
    "id": "11111111-2222-3333-4444-555555555555",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "IGV 18%",
    "rate": 18.00,
    "code": "1000",
    "isInclusive": true,
    "isActive": true,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T14:00:00Z"
  }
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Impuesto no encontrado",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/taxes/11111111-2222-3333-4444-555555555555",
  "method": "GET"
}
```

---

#### POST `/api/v1/tenants/{tenantId}/taxes`

> Crea un nuevo impuesto tributario para el restaurante.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `tenantId` | UUID | Sí | ID del restaurante |

**Body (JSON) — `TaxRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `name` | string | Sí | No vacío, máx. 50 caracteres | Nombre del impuesto (ej. `"IGV 18%"`) |
| `rate` | BigDecimal | Sí | `0.00` a `100.00`, máx. 3 enteros y 2 decimales | Tasa porcentual |
| `code` | string | No | Máx. 20 caracteres | Código tributario (ej. `"1000"`) |
| `isInclusive` | boolean | No | Default: `true` | Si el impuesto ya está incluido en el precio |
| `isActive` | boolean | No | Default: `true` | Estado activo |

**Ejemplo de request:**
```json
{
  "name": "IGV 18%",
  "rate": 18.00,
  "code": "1000",
  "isInclusive": true,
  "isActive": true
}
```

##### 📤 Response

**Éxito (`201 Created`) — Envelope `ApiResponse<TaxResponse>`:**
```json
{
  "success": true,
  "message": "Impuesto creado exitosamente",
  "data": {
    "id": "11111111-2222-3333-4444-555555555555",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "IGV 18%",
    "rate": 18.00,
    "code": "1000",
    "isInclusive": true,
    "isActive": true,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T14:00:00Z"
  }
}
```

**Error (`400` | `401` | `403` | `409`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Error de validación: rate: La tasa debe ser menor o igual a 100.00",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/taxes",
  "method": "POST"
}
```

---

#### PUT `/api/v1/tenants/{tenantId}/taxes/{taxId}`

> Actualiza un impuesto existente e invalida `storefront_menu`. Si el impuesto ya está asignado a productos o ítems de órdenes históricas, **no permite modificar `rate`** (`400 Bad Request`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `tenantId` | UUID | Sí | ID del restaurante |
| `taxId` | UUID | Sí | ID del impuesto |

**Body (JSON):** Mismo formato `TaxRequest`.

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<TaxResponse>`:**
```json
{
  "success": true,
  "message": "Impuesto actualizado exitosamente",
  "data": {
    "id": "11111111-2222-3333-4444-555555555555",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "IGV 18% (SUNAT)",
    "rate": 18.00,
    "code": "1000",
    "isInclusive": true,
    "isActive": true,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T15:00:00Z"
  }
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No se puede modificar la tasa de un impuesto ya utilizado en productos u órdenes",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T15:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/taxes/11111111-2222-3333-4444-555555555555",
  "method": "PUT"
}
```

---

#### PATCH `/api/v1/tenants/{tenantId}/taxes/{taxId}/status`

> Activa o desactiva un impuesto del restaurante e invalida `storefront_menu`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `taxId` (UUID).

**Body (JSON) — `TaxStatusRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `isActive` | boolean | Sí | `@NotNull` | Nuevo estado del impuesto |

**Ejemplo de request:**
```json
{
  "isActive": false
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<TaxResponse>`:**
```json
{
  "success": true,
  "message": "Impuesto desactivado exitosamente",
  "data": {
    "id": "11111111-2222-3333-4444-555555555555",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "IGV 18%",
    "rate": 18.00,
    "code": "1000",
    "isInclusive": true,
    "isActive": false,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T15:05:00Z"
  }
}
```

*(Si `isActive` es `true`, `message` será `"Impuesto activado exitosamente"`.)*

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Impuesto no encontrado",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T15:05:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/taxes/11111111-2222-3333-4444-555555555555/status",
  "method": "PATCH"
}
```

---

#### DELETE `/api/v1/tenants/{tenantId}/taxes/{taxId}`

> Elimina físicamente un impuesto siempre que no esté referenciado por ningún producto ni orden histórica (en caso contrario responde `400 Bad Request`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `taxId` (UUID).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<Void>`:**
```json
{
  "success": true,
  "message": "Impuesto eliminado exitosamente",
  "data": null
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No se puede eliminar un impuesto que ya está asociado a productos u órdenes; desactívelo en su lugar",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T15:10:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/taxes/11111111-2222-3333-4444-555555555555",
  "method": "DELETE"
}
```

#### 💡 Reglas de Cálculo Tributario en NexoFood

- **Impuesto Inclusivo (`isInclusive = true`, ej. IGV 18% en Perú):**
  `baseUnitPrice = sellingPrice / (1 + rate / 100)`, `subtotal = baseUnitPrice × quantity`, `taxAmount = (sellingPrice × quantity) - subtotal`.
- **Impuesto Exclusivo (`isInclusive = false`):**
  `baseUnitPrice = sellingPrice`, `subtotal = baseUnitPrice × quantity`, `taxAmount = subtotal × (rate / 100)`.
- **Exento o sin impuesto activo (`tax == null`, `!isActive` o `rate <= 0`):**
  `taxRate = 0.00`, `taxAmount = 0.00`, `baseUnitPrice = sellingPrice`.

---

### 📂 Categorías de Productos (`CategoryController`)

#### GET `/api/v1/tenants/{tenantId}/categories`

> Lista las categorías del restaurante ordenadas ascendentemente por `sortOrder`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID).

**Query Parameters:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `activeOnly` | boolean | No | Si es `true`, retorna solo categorías activas (default: `false`) |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<List<CategoryResponse>>`:**
```json
{
  "success": true,
  "message": "Categorías obtenidas exitosamente",
  "data": [
    {
      "id": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "name": "Hamburguesas Clásicas",
      "description": "Hamburguesas artesanales a la parrilla",
      "sortOrder": 1,
      "isActive": true,
      "createdAt": "2026-11-18T14:00:00Z",
      "updatedAt": "2026-11-18T14:00:00Z"
    }
  ]
}
```

**Error (`401` | `403`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Acceso denegado: no tiene permisos para realizar esta operación",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/categories",
  "method": null
}
```

---

#### GET `/api/v1/tenants/{tenantId}/categories/{categoryId}`

> Obtiene el detalle de una categoría por su ID.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `categoryId` (UUID).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<CategoryResponse>`:**
```json
{
  "success": true,
  "message": "Categoría obtenida exitosamente",
  "data": {
    "id": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Hamburguesas Clásicas",
    "description": "Hamburguesas artesanales a la parrilla",
    "sortOrder": 1,
    "isActive": true,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T14:00:00Z"
  }
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Categoría no encontrada",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/categories/c1a2c3d4-e5f6-7890-abcd-ef1234567890",
  "method": "GET"
}
```

---

#### POST `/api/v1/tenants/{tenantId}/categories`

> Crea una nueva categoría en el restaurante (valida nombre único case-insensitive dentro del tenant) e invalida las cachés del storefront.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID).

**Body (JSON) — `CategoryRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `name` | string | Sí | No vacío, máx. 100 caracteres, único en tenant | Nombre de la categoría |
| `description` | string | No | Máx. 500 caracteres | Descripción para la carta |
| `sortOrder` | integer | No | `≥ 0` (default: `0`) | Posición de ordenamiento |
| `isActive` | boolean | No | Default: `true` | Si se muestra en el storefront |

**Ejemplo de request:**
```json
{
  "name": "Hamburguesas Clásicas",
  "description": "Hamburguesas artesanales a la parrilla",
  "sortOrder": 1,
  "isActive": true
}
```

##### 📤 Response

**Éxito (`201 Created`) — Envelope `ApiResponse<CategoryResponse>`:**
```json
{
  "success": true,
  "message": "Categoría creada exitosamente",
  "data": {
    "id": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Hamburguesas Clásicas",
    "description": "Hamburguesas artesanales a la parrilla",
    "sortOrder": 1,
    "isActive": true,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T14:00:00Z"
  }
}
```

**Error (`400` | `401` | `403` | `409`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Ya existe una categoría con ese nombre en el restaurante",
  "error": "CONFLICT",
  "status": 409,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/categories",
  "method": "POST"
}
```

---

#### PUT `/api/v1/tenants/{tenantId}/categories/{categoryId}`

> Actualiza el nombre, descripción, orden o estado de una categoría.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `categoryId` (UUID).  
**Body (JSON):** Mismo formato `CategoryRequest`.

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<CategoryResponse>`:**
```json
{
  "success": true,
  "message": "Categoría actualizada exitosamente",
  "data": {
    "id": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Hamburguesas Premium",
    "description": "Carne Angus 100%",
    "sortOrder": 1,
    "isActive": true,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T15:00:00Z"
  }
}
```

**Error (`400` | `401` | `403` | `404` | `409`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Categoría no encontrada",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T15:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/categories/c1a2c3d4-e5f6-7890-abcd-ef1234567890",
  "method": "PUT"
}
```

---

#### PATCH `/api/v1/tenants/{tenantId}/categories/{categoryId}/status`

> Activa o desactiva una categoría. Al desactivarla, sus productos dejan de mostrarse en el storefront y se marcan como no disponibles en el carrito/checkout.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `categoryId` (UUID).

**Body (JSON) — `CategoryStatusRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `isActive` | boolean | Sí | `@NotNull` | `true` para activar, `false` para desactivar |

**Ejemplo de request:**
```json
{
  "isActive": false
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<CategoryResponse>`:**
```json
{
  "success": true,
  "message": "Categoría desactivada exitosamente",
  "data": {
    "id": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Hamburguesas Premium",
    "description": "Carne Angus 100%",
    "sortOrder": 1,
    "isActive": false,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T15:05:00Z"
  }
}
```

*(Si `isActive` es `true`, `message` será `"Categoría activada exitosamente"`.)*

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Categoría no encontrada",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T15:05:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/categories/c1a2c3d4-e5f6-7890-abcd-ef1234567890/status",
  "method": "PATCH"
}
```

---

#### PATCH `/api/v1/tenants/{tenantId}/categories/{categoryId}/sort-order`

> Cambia la posición de visualización (`sortOrder`) de una categoría.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `categoryId` (UUID).

**Body (JSON) — `CategorySortOrderRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `sortOrder` | integer | Sí | `@NotNull`, `≥ 0` | Nueva posición en la carta |

**Ejemplo de request:**
```json
{
  "sortOrder": 2
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<CategoryResponse>`:**
```json
{
  "success": true,
  "message": "Orden de la categoría actualizado exitosamente",
  "data": {
    "id": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Hamburguesas Premium",
    "description": "Carne Angus 100%",
    "sortOrder": 2,
    "isActive": true,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T15:10:00Z"
  }
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Error de validación: sortOrder: El orden debe ser mayor o igual a 0",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T15:10:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/categories/c1a2c3d4-e5f6-7890-abcd-ef1234567890/sort-order",
  "method": "PATCH"
}
```

---

#### DELETE `/api/v1/tenants/{tenantId}/categories/{categoryId}`

> Elimina físicamente una categoría si no tiene productos asociados (de lo contrario responde `400 Bad Request`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `categoryId` (UUID).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<Void>`:**
```json
{
  "success": true,
  "message": "Categoría eliminada exitosamente",
  "data": null
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No se puede eliminar la categoría porque tiene productos asociados",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T15:15:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/categories/c1a2c3d4-e5f6-7890-abcd-ef1234567890",
  "method": "DELETE"
}
```

---

### 🍔 Productos y Stock (`ProductController`)

#### GET `/api/v1/tenants/{tenantId}/products`

> Lista paginada de productos del restaurante con filtros opcionales por categoría, disponibilidad y stock disponible.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID).

**Query Parameters:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `categoryId` | UUID | No | Filtrar por categoría |
| `availableOnly` | boolean | No | Si es `true`, solo productos con `isAvailable = true` |
| `inStockOnly` | boolean | No | Si es `true`, solo productos con `stock > 0` |
| `page` | integer | No | Página 0-indexed (default: `0`) |
| `size` | integer | No | Elementos por página (default: `20`) |
| `sort` | string | No | Ordenamiento (ej. `name,asc`) |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<PageResponse<ProductResponse>>`:**
```json
{
  "success": true,
  "message": "Productos obtenidos exitosamente",
  "data": {
    "content": [
      {
        "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
        "categoryId": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
        "categoryName": "Hamburguesas Clásicas",
        "taxId": "11111111-2222-3333-4444-555555555555",
        "taxName": "IGV 18%",
        "taxRate": 18.00,
        "taxInclusive": true,
        "name": "Hamburguesa Doble Queso",
        "description": "Doble carne Angus con cheddar y tocino",
        "price": 24.90,
        "basePrice": 24.90,
        "prices": [
          {
            "id": "99999999-8888-7777-6666-555555555555",
            "productId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
            "name": "Precio Base",
            "price": 24.90,
            "isBase": true,
            "discountPercentage": null,
            "startDate": null,
            "endDate": null,
            "startTime": null,
            "endTime": null,
            "daysOfWeek": null,
            "priority": 0,
            "isActive": true,
            "createdAt": "2026-11-18T14:00:00Z",
            "updatedAt": "2026-11-18T14:00:00Z"
          }
        ],
        "imageUrl": "https://storage.nexofood.lat/products/doble-queso.png",
        "stock": 25,
        "isAvailable": true,
        "createdAt": "2026-11-18T14:00:00Z",
        "updatedAt": "2026-11-18T14:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

**Error (`401` | `403`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Acceso denegado: no tiene permisos para realizar esta operación",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/products",
  "method": null
}
```

---

#### GET `/api/v1/tenants/{tenantId}/products/{productId}`

> Obtiene el detalle completo de un producto del restaurante, incluyendo su stock actual y todas sus reglas de precio.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `productId` (UUID).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<ProductResponse>`:**
```json
{
  "success": true,
  "message": "Producto obtenido exitosamente",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "categoryId": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "categoryName": "Hamburguesas Clásicas",
    "taxId": "11111111-2222-3333-4444-555555555555",
    "taxName": "IGV 18%",
    "taxRate": 18.00,
    "taxInclusive": true,
    "name": "Hamburguesa Doble Queso",
    "description": "Doble carne Angus con cheddar y tocino",
    "price": 24.90,
    "basePrice": 24.90,
    "prices": [],
    "imageUrl": "https://storage.nexofood.lat/products/doble-queso.png",
    "stock": 25,
    "isAvailable": true,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T14:00:00Z"
  }
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Producto no encontrado",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/products/3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "method": "GET"
}
```

---

#### POST `/api/v1/tenants/{tenantId}/products`

> Crea un nuevo producto con su stock inicial (`stock`, default `0`) y precio base. Valida que la suscripción esté activa y no supere el límite `maxProducts` del plan contratado. Debe definirse **exactamente un precio base activo** (ya sea con el campo `price` o con un elemento `isBase: true` en `prices`). Al guardar el producto, evalúa proactivamente el stock: si `stock == 0` dispara `OUT_OF_STOCK` o si `0 < stock <= lowStockThreshold` (`5` por defecto) dispara `LOW_STOCK` hacia `OWNER`, `ADMIN` y `COCINERO`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID).

**Body (JSON) — `ProductRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `categoryId` | UUID | No | Categoría del mismo tenant | Categoría asignada |
| `taxId` | UUID | No | Impuesto activo del mismo tenant | Impuesto aplicable |
| `name` | string | Sí | No vacío, máx. 150 caracteres | Nombre del producto |
| `description` | string | No | Máx. 1000 caracteres | Descripción / ingredientes |
| `price` | BigDecimal | Condicional | `0.01` a `999999.99` | Precio base (obligatorio salvo que se envíe una regla `isBase: true` en `prices`) |
| `imageUrl` | string | No | Máx. 500 caracteres | URL de imagen |
| `stock` | integer | No | `≥ 0` (default: `0`) | Unidades iniciales en stock |
| `isAvailable` | boolean | No | Default: `true` | Disponibilidad para ordenar |
| `prices` | `List<ProductPriceRequest>` | No | `@Valid` | Reglas de precios/promociones iniciales |

**Ejemplo de request:**
```json
{
  "categoryId": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
  "taxId": "11111111-2222-3333-4444-555555555555",
  "name": "Hamburguesa Doble Queso",
  "description": "Doble carne Angus con cheddar y tocino",
  "price": 24.90,
  "imageUrl": "https://storage.nexofood.lat/products/doble-queso.png",
  "stock": 25,
  "isAvailable": true
}
```

##### 📤 Response

**Éxito (`201 Created`) — Envelope `ApiResponse<ProductResponse>`:**
```json
{
  "success": true,
  "message": "Producto creado exitosamente",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "categoryId": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "categoryName": "Hamburguesas Clásicas",
    "taxId": "11111111-2222-3333-4444-555555555555",
    "taxName": "IGV 18%",
    "taxRate": 18.00,
    "taxInclusive": true,
    "name": "Hamburguesa Doble Queso",
    "description": "Doble carne Angus con cheddar y tocino",
    "price": 24.90,
    "basePrice": 24.90,
    "prices": [],
    "imageUrl": "https://storage.nexofood.lat/products/doble-queso.png",
    "stock": 25,
    "isAvailable": true,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T14:00:00Z"
  }
}
```

**Error (`400` | `401` | `403` | `404` | `409`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Has alcanzado el límite máximo de productos permitidos por tu plan",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/products",
  "method": "POST"
}
```

---

#### PUT `/api/v1/tenants/{tenantId}/products/{productId}`

> Actualiza un producto con bloqueo pesimista (`FOR UPDATE`). Enviar `categoryId: null` o `taxId: null` desasocia la categoría/impuesto. Si se envía `price`, actualiza el precio base. Si tras guardar el stock es `<= lowStockThreshold` (`5` por defecto), dispara alertas `LOW_STOCK` u `OUT_OF_STOCK` al staff operativo (`OWNER`, `ADMIN`, `COCINERO`). **Nota:** El arreglo `prices` no se procesa en `PUT` (usar `ProductPriceController`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `productId` (UUID).  
**Body (JSON):** Mismo formato `ProductRequest`.

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<ProductResponse>`:**
```json
{
  "success": true,
  "message": "Producto actualizado exitosamente",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "categoryId": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "categoryName": "Hamburguesas Clásicas",
    "taxId": "11111111-2222-3333-4444-555555555555",
    "taxName": "IGV 18%",
    "taxRate": 18.00,
    "taxInclusive": true,
    "name": "Hamburguesa Doble Queso Deluxe",
    "description": "Doble carne Angus, cheddar y salsa especial",
    "price": 26.90,
    "basePrice": 26.90,
    "prices": [],
    "imageUrl": "https://storage.nexofood.lat/products/doble-queso.png",
    "stock": 25,
    "isAvailable": true,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T15:00:00Z"
  }
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Producto no encontrado",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T15:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/products/3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "method": "PUT"
}
```

---

#### PATCH `/api/v1/tenants/{tenantId}/products/{productId}/availability`

> Activa o desactiva la disponibilidad de un producto e invalida `storefront_menu`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `productId` (UUID).

**Body (JSON) — `ProductAvailabilityRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `isAvailable` | boolean | Sí | `@NotNull` | `true` para disponible, `false` para no disponible |

**Ejemplo de request:**
```json
{
  "isAvailable": false
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<ProductResponse>`:**
```json
{
  "success": true,
  "message": "Producto marcado como no disponible",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Hamburguesa Doble Queso",
    "stock": 25,
    "isAvailable": false
  }
}
```

*(Si `isAvailable` es `true`, `message` será `"Producto marcado como disponible"`.)*

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Producto no encontrado",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T15:05:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/products/3fa85f64-5717-4562-b3fc-2c963f66afa6/availability",
  "method": "PATCH"
}
```

---

#### PATCH `/api/v1/tenants/{tenantId}/products/{productId}/stock`

> Establece directamente el stock entero disponible de un producto (`stock >= 0`) con bloqueo pesimista (`SELECT ... FOR UPDATE`), invalida `storefront_menu` y evalúa alertas proactivas de inventario (`OUT_OF_STOCK` si `stock == 0`, `LOW_STOCK` si `0 < stock <= lowStockThreshold`, configurado en `5` por defecto).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `productId` (UUID).

**Body (JSON) — `ProductStockRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `stock` | integer | Sí | `@NotNull`, `≥ 0` | Nuevo stock absoluto disponible |

**Ejemplo de request:**
```json
{
  "stock": 50
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<ProductResponse>`:**
```json
{
  "success": true,
  "message": "Stock del producto actualizado exitosamente",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Hamburguesa Doble Queso",
    "stock": 50,
    "isAvailable": true
  }
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Error de validación: stock: El stock debe ser mayor o igual a 0",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T15:10:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/products/3fa85f64-5717-4562-b3fc-2c963f66afa6/stock",
  "method": "PATCH"
}
```

---

#### DELETE `/api/v1/tenants/{tenantId}/products/{productId}`

> Deshabilita lógicamente un producto (`isAvailable = false`) preservando integridad referencial en órdenes históricas e invalida `storefront_menu`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `productId` (UUID).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<Void>`:**
```json
{
  "success": true,
  "message": "Producto deshabilitado exitosamente",
  "data": null
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Producto no encontrado",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T15:15:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/products/3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "method": "DELETE"
}
```

---

### 🏷️ Reglas de Precio y Promociones (`ProductPriceController`)

> **Reglas de negocio:** Todo producto mantiene exactamente **un precio base activo** (`isBase = true`, `isActive = true`). Se admiten rangos horarios nocturnos que cruzan la medianoche (`startTime > endTime`), días ISO (`1=Lunes ... 7=Domingo`) y resolución por mayor `priority`. Dos promociones activas con la misma `priority` no pueden solaparse en fechas/días/horas (`409 Conflict`).

#### GET `/api/v1/tenants/{tenantId}/products/{productId}/prices`

> Lista todas las reglas de precio configuradas para un producto.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `productId` (UUID).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<List<ProductPriceResponse>>`:**
```json
{
  "success": true,
  "message": "Reglas de precio obtenidas exitosamente",
  "data": [
    {
      "id": "99999999-8888-7777-6666-555555555555",
      "productId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "name": "Happy Hour Jueves",
      "price": 19.90,
      "isBase": false,
      "discountPercentage": 20.00,
      "startDate": "2026-11-01",
      "endDate": "2026-12-31",
      "startTime": "18:00:00",
      "endTime": "22:00:00",
      "daysOfWeek": [4, 5],
      "priority": 10,
      "isActive": true,
      "createdAt": "2026-11-18T14:00:00Z",
      "updatedAt": "2026-11-18T14:00:00Z"
    }
  ]
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Producto no encontrado",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/products/3fa85f64-5717-4562-b3fc-2c963f66afa6/prices",
  "method": "GET"
}
```

---

#### GET `/api/v1/tenants/{tenantId}/products/{productId}/prices/{priceId}`

> Obtiene el detalle de una regla de precio específica del producto.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `productId` (UUID), `priceId` (UUID).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<ProductPriceResponse>`:**
```json
{
  "success": true,
  "message": "Regla de precio obtenida exitosamente",
  "data": {
    "id": "99999999-8888-7777-6666-555555555555",
    "productId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "Happy Hour Jueves",
    "price": 19.90,
    "isBase": false,
    "discountPercentage": 20.00,
    "startDate": "2026-11-01",
    "endDate": "2026-12-31",
    "startTime": "18:00:00",
    "endTime": "22:00:00",
    "daysOfWeek": [4, 5],
    "priority": 10,
    "isActive": true,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T14:00:00Z"
  }
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Regla de precio no encontrada",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/products/3fa85f64-5717-4562-b3fc-2c963f66afa6/prices/99999999-8888-7777-6666-555555555555",
  "method": "GET"
}
```

---

#### POST `/api/v1/tenants/{tenantId}/products/{productId}/prices`

> Agrega una nueva regla de precio o promoción al producto, registra auditoría de cambio de precio e invalida `storefront_menu`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `productId` (UUID).

**Body (JSON) — `ProductPriceRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `id` | UUID | No | Ignorado en `POST` | Reservado para payloads internos |
| `name` | string | Sí | No vacío, máx. 100 caracteres | Nombre de la regla |
| `price` | BigDecimal | Sí | `@NotNull`, `≥ 0.00` | Precio aplicable |
| `isBase` | boolean | No | Default: `false` (solo 1 base activo) | Indica si es el precio base |
| `discountPercentage` | BigDecimal | No | `0.00` a `100.00` | Porcentaje informativo de descuento |
| `startDate` | string | No | `YYYY-MM-DD` (`endDate >= startDate`) | Fecha de inicio de vigencia |
| `endDate` | string | No | `YYYY-MM-DD` | Fecha de fin de vigencia |
| `startTime` | string | No | `HH:mm:ss` (`startTime != endTime`) | Hora de inicio |
| `endTime` | string | No | `HH:mm:ss` | Hora de fin |
| `daysOfWeek` | Short[] | No | Valores entre `1` (Lun) y `7` (Dom) | Días ISO aplicables |
| `priority` | integer | No | Default: `0` | Prioridad (mayor valor gana) |
| `isActive` | boolean | No | Default: `true` | Estado activo |

**Ejemplo de request:**
```json
{
  "name": "Happy Hour Jueves",
  "price": 19.90,
  "isBase": false,
  "discountPercentage": 20.00,
  "startDate": "2026-11-01",
  "endDate": "2026-12-31",
  "startTime": "18:00:00",
  "endTime": "22:00:00",
  "daysOfWeek": [4, 5],
  "priority": 10,
  "isActive": true
}
```

##### 📤 Response

**Éxito (`201 Created`) — Envelope `ApiResponse<ProductPriceResponse>`:**
```json
{
  "success": true,
  "message": "Regla de precio creada exitosamente",
  "data": {
    "id": "99999999-8888-7777-6666-555555555555",
    "productId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "Happy Hour Jueves",
    "price": 19.90,
    "isBase": false,
    "discountPercentage": 20.00,
    "startDate": "2026-11-01",
    "endDate": "2026-12-31",
    "startTime": "18:00:00",
    "endTime": "22:00:00",
    "daysOfWeek": [4, 5],
    "priority": 10,
    "isActive": true,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T14:00:00Z"
  }
}
```

**Error (`400` | `401` | `403` | `404` | `409`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Existe un solapamiento con otra regla de precio activa de la misma prioridad",
  "error": "CONFLICT",
  "status": 409,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/products/3fa85f64-5717-4562-b3fc-2c963f66afa6/prices",
  "method": "POST"
}
```

---

#### PUT `/api/v1/tenants/{tenantId}/products/{productId}/prices/{priceId}`

> Actualiza una regla de precio existente del producto manteniendo la invariante de 1 precio base activo y sin solapamientos de igual prioridad.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `productId` (UUID), `priceId` (UUID).  
**Body (JSON):** Mismo formato `ProductPriceRequest`.

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<ProductPriceResponse>`:**
```json
{
  "success": true,
  "message": "Regla de precio actualizada exitosamente",
  "data": {
    "id": "99999999-8888-7777-6666-555555555555",
    "productId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "Happy Hour Fin de Semana",
    "price": 18.90,
    "isBase": false,
    "discountPercentage": 24.00,
    "startDate": "2026-11-01",
    "endDate": "2026-12-31",
    "startTime": "18:00:00",
    "endTime": "23:00:00",
    "daysOfWeek": [5, 6],
    "priority": 10,
    "isActive": true,
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T15:00:00Z"
  }
}
```

**Error (`400` | `401` | `403` | `404` | `409`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No se puede desactivar el único precio base activo del producto",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T15:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/products/3fa85f64-5717-4562-b3fc-2c963f66afa6/prices/99999999-8888-7777-6666-555555555555",
  "method": "PUT"
}
```

---

#### DELETE `/api/v1/tenants/{tenantId}/products/{productId}/prices/{priceId}`

> Elimina una regla de precio promocional. Rechaza con `400 Bad Request` si se intenta eliminar el precio base activo.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `productId` (UUID), `priceId` (UUID).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<Void>`:**
```json
{
  "success": true,
  "message": "Regla de precio eliminada exitosamente",
  "data": null
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No se puede eliminar el precio base activo del producto",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T15:10:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/products/3fa85f64-5717-4562-b3fc-2c963f66afa6/prices/99999999-8888-7777-6666-555555555555",
  "method": "DELETE"
}
```

---

### 🌐 Menú Público del Storefront (`StorefrontCatalogController`)

> **🏪 Multi-Tenant (Público):** Todos estos endpoints requieren que el restaurante identificado por `slug` exista y esté activo (`isActive = true`); si no existe o está inactivo responden `404 Not Found`.

#### GET `/api/v1/store/{slug}/menu`

> Retorna el menú completo del restaurante activo: categorías activas ordenadas por `sortOrder` con sus productos disponibles, productos sin categoría (`uncategorizedProducts`), precio vigente evaluado en tiempo real, desglose tributario, horario (`openingTime`, `closingTime`), estado efectivo `isOpen` y bandera `onlinePaymentsEnabled`.

**Autenticación:** No requerida (Público)  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `slug` (string).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<StorefrontMenuResponse>`:**
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
    "openingTime": "09:00:00",
    "closingTime": "23:00:00",
    "isOpen": true,
    "categories": [
      {
        "id": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
        "name": "Hamburguesas Clásicas",
        "description": "Hamburguesas artesanales a la parrilla",
        "sortOrder": 1,
        "products": [
          {
            "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
            "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
            "categoryId": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
            "categoryName": "Hamburguesas Clásicas",
            "name": "Hamburguesa Doble Queso",
            "description": "Doble carne Angus con cheddar y tocino",
            "imageUrl": "https://storage.nexofood.lat/products/doble-queso.png",
            "price": 19.90,
            "basePrice": 24.90,
            "activePromotionName": "Happy Hour Jueves",
            "discountPercentage": 20.00,
            "taxId": "11111111-2222-3333-4444-555555555555",
            "taxName": "IGV 18%",
            "taxRate": 18.00,
            "taxInclusive": true,
            "baseUnitPrice": 16.86,
            "taxAmount": 3.04,
            "finalPrice": 19.90,
            "stock": 25,
            "isAvailable": true
          }
        ]
      }
    ],
    "uncategorizedProducts": []
  }
}
```

**Error (`404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "El restaurante 'Burger Bros' no se encuentra activo",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/store/burger-bros/menu",
  "method": "GET"
}
```

---

#### GET `/api/v1/store/{slug}/categories`

> Lista las categorías activas del restaurante ordenadas ascendentemente por `sortOrder`.

**Autenticación:** No requerida (Público)  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `slug` (string).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<List<CategoryResponse>>`:**
```json
{
  "success": true,
  "message": "Categorías de la tienda obtenidas exitosamente",
  "data": [
    {
      "id": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "name": "Hamburguesas Clásicas",
      "description": "Hamburguesas artesanales a la parrilla",
      "sortOrder": 1,
      "isActive": true,
      "createdAt": "2026-11-18T14:00:00Z",
      "updatedAt": "2026-11-18T14:00:00Z"
    }
  ]
}
```

**Error (`404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Restaurante no encontrado con slug: burger-bros",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/store/burger-bros/categories",
  "method": "GET"
}
```

---

#### GET `/api/v1/store/{slug}/products`

> Lista paginada de productos disponibles en la tienda pública, con filtro opcional por categoría activa y búsqueda parcial por nombre (case-insensitive).

**Autenticación:** No requerida (Público)  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `slug` (string).

**Query Parameters:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `categoryId` | UUID | No | Filtra productos de una categoría activa |
| `search` | string | No | Coincidencia parcial en el nombre (case-insensitive) |
| `page` | integer | No | Página 0-indexed (default: `0`) |
| `size` | integer | No | Elementos por página (default: `20`) |
| `sort` | string | No | Ordenamiento |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<PageResponse<StorefrontProductResponse>>`:**
```json
{
  "success": true,
  "message": "Productos de la tienda obtenidos exitosamente",
  "data": {
    "content": [
      {
        "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
        "categoryId": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
        "categoryName": "Hamburguesas Clásicas",
        "name": "Hamburguesa Doble Queso",
        "description": "Doble carne Angus con cheddar y tocino",
        "imageUrl": "https://storage.nexofood.lat/products/doble-queso.png",
        "price": 24.90,
        "basePrice": 24.90,
        "activePromotionName": null,
        "discountPercentage": null,
        "taxId": "11111111-2222-3333-4444-555555555555",
        "taxName": "IGV 18%",
        "taxRate": 18.00,
        "taxInclusive": true,
        "baseUnitPrice": 21.10,
        "taxAmount": 3.80,
        "finalPrice": 24.90,
        "stock": 25,
        "isAvailable": true
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

**Error (`404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Categoría no encontrada en esta tienda",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/store/burger-bros/products",
  "method": "GET"
}
```

---

#### GET `/api/v1/store/{slug}/products/{id}`

> Obtiene el detalle público de un producto disponible con su precio vigente, promoción activa y desglose tributario. Si el producto está marcado como no disponible o su categoría está inactiva, responde `404 Not Found`.

**Autenticación:** No requerida (Público)  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `slug` (string), `id` (UUID del producto).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<StorefrontProductResponse>`:**
```json
{
  "success": true,
  "message": "Producto obtenido exitosamente",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "categoryId": "c1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "categoryName": "Hamburguesas Clásicas",
    "name": "Hamburguesa Doble Queso",
    "description": "Doble carne Angus con cheddar y tocino",
    "imageUrl": "https://storage.nexofood.lat/products/doble-queso.png",
    "price": 24.90,
    "basePrice": 24.90,
    "activePromotionName": null,
    "discountPercentage": null,
    "taxId": "11111111-2222-3333-4444-555555555555",
    "taxName": "IGV 18%",
    "taxRate": 18.00,
    "taxInclusive": true,
    "baseUnitPrice": 21.10,
    "taxAmount": 3.80,
    "finalPrice": 24.90,
    "stock": 25,
    "isAvailable": true
  }
}
```

**Error (`404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "El producto solicitado no se encuentra disponible actualmente",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/store/burger-bros/products/3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "method": "GET"
}
```

> **📝 Notas para el Frontend (Catalog):**
> - Todos los IDs son **UUID** y las fechas llegan en **ISO 8601** (`startDate`/`endDate` en `YYYY-MM-DD`, `startTime`/`endTime` en `HH:mm:ss`).
> - En `StorefrontMenuResponse`, revisar `isOpen` (junto con `openingTime` y `closingTime`) para indicar al cliente si el local está recibiendo pedidos en ese momento.
> - En `StorefrontProductResponse`, mostrar `finalPrice` al comensal; si `activePromotionName != null` o `price < basePrice`, tachar `basePrice` y resaltar el descuento.
> - Si `stock === 0`, deshabilitar el botón de agregar al carrito y mostrar la etiqueta "Agotado".
> - Al crear/actualizar un producto o ajustar su stock, y también al consumirse inventario en pedidos confirmados, el backend dispara automáticamente alertas `LOW_STOCK` / `OUT_OF_STOCK` (`stock <= 5` por defecto).
> - Al actualizar un producto con `PUT /api/v1/tenants/{tenantId}/products/{productId}`, enviar `categoryId` y `taxId` actuales si no se desean limpiar; gestionar reglas promocionales siempre a través de `/prices`.

---

## 5. Cart — Carrito de Compras (`CartController`)

> **🏪 Multi-Tenant:** Cada cliente dispone de **un único carrito persistente por restaurante** (`tenantId, customerId`). El servidor resuelve automáticamente al cliente a partir del JWT y del `slug` de la tienda (creando su ficha `TenantCustomer` si no existe, o rechazando con `403 Forbidden` si está bloqueado). Cada lectura o modificación recalcula los precios dinámicos vigentes y valida disponibilidad y stock en vivo.

---

#### GET `/api/v1/store/{slug}/cart`

> Obtiene (o crea vacío si no existe) el carrito del comensal autenticado en la tienda, reconciliando precios vigentes, disponibilidad de producto/categoría y stock contra las cantidades solicitadas.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquier cliente autenticado no bloqueado en la tienda  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `slug` | string | Sí | Slug de la tienda |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<CartResponse>`:**
```json
{
  "success": true,
  "message": "Carrito obtenido exitosamente",
  "data": {
    "id": "a9b8c7d6-e5f4-3210-abcd-ef1234567890",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantSlug": "burger-bros",
    "tenantName": "Burger Bros",
    "customerId": "7fa85f64-5717-4562-b3fc-2c963f66afa9",
    "customerUserId": "7fa85f64-5717-4562-b3fc-2c963f66afa9",
    "total": 49.80,
    "notes": "Enviar cubiertos y servilletas",
    "items": [
      {
        "id": "1a2b3c4d-5e6f-7890-abcd-ef1234567890",
        "productId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "productName": "Hamburguesa Doble Queso",
        "productImageUrl": "https://storage.nexofood.lat/products/doble-queso.png",
        "unitPrice": 24.90,
        "previousUnitPrice": null,
        "priceChanged": false,
        "productAvailable": true,
        "productStock": 25,
        "quantity": 2,
        "subtotal": 49.80,
        "notes": "Sin cebolla",
        "createdAt": "2026-11-18T14:00:00Z",
        "updatedAt": "2026-11-18T14:00:00Z"
      }
    ],
    "hasPriceChanges": false,
    "hasUnavailableItems": false,
    "warnings": [],
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T14:00:00Z"
  }
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Tu cuenta está bloqueada en este restaurante",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/store/burger-bros/cart",
  "method": "GET"
}
```

---

#### POST `/api/v1/store/{slug}/cart/items`

> Agrega un producto al carrito validando disponibilidad, categoría activa y stock suficiente para la cantidad acumulada. Si el producto ya existe en el carrito, suma la cantidad y actualiza las notas si se envían no vacías. **Retorna `200 OK`.**

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cliente autenticado no bloqueado  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `slug` (string).

**Body (JSON) — `CartItemRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `productId` | UUID | Sí | `@NotNull` | ID del producto de esta tienda |
| `quantity` | integer | Sí | `@NotNull`, `1` a `999` | Cantidad a agregar |
| `notes` | string | No | Máx. 500 caracteres | Preferencias de preparación |

**Ejemplo de request:**
```json
{
  "productId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "quantity": 2,
  "notes": "Sin cebolla"
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<CartResponse>`:**
```json
{
  "success": true,
  "message": "Producto agregado al carrito exitosamente",
  "data": {
    "id": "a9b8c7d6-e5f4-3210-abcd-ef1234567890",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantSlug": "burger-bros",
    "tenantName": "Burger Bros",
    "customerId": "7fa85f64-5717-4562-b3fc-2c963f66afa9",
    "customerUserId": "7fa85f64-5717-4562-b3fc-2c963f66afa9",
    "total": 49.80,
    "notes": null,
    "items": [],
    "hasPriceChanges": false,
    "hasUnavailableItems": false,
    "warnings": [],
    "createdAt": "2026-11-18T14:00:00Z",
    "updatedAt": "2026-11-18T14:01:00Z"
  }
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Stock insuficiente para 'Hamburguesa Doble Queso'. Disponible: 1",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T14:01:00",
  "path": "/api/v1/store/burger-bros/cart/items",
  "method": "POST"
}
```

---

#### PATCH `/api/v1/store/{slug}/cart/items/{itemId}`

> Actualiza la cantidad (validada contra stock disponible) y/o las notas de un ítem del carrito. Debe enviarse al menos `quantity` o `notes`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cliente propietario del carrito  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `slug` (string), `itemId` (UUID del ítem en carrito).

**Body (JSON) — `CartItemUpdateRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `quantity` | integer | No* | `1` a `999` | Nueva cantidad total del ítem |
| `notes` | string | No* | Máx. 500 caracteres | Nuevas observaciones de preparación |

*\*Al menos uno de los dos campos debe ser enviado no nulo.*

**Ejemplo de request:**
```json
{
  "quantity": 3,
  "notes": "Bien cocida"
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<CartResponse>`:**
```json
{
  "success": true,
  "message": "Ítem del carrito actualizado exitosamente",
  "data": {
    "id": "a9b8c7d6-e5f4-3210-abcd-ef1234567890",
    "total": 74.70,
    "hasPriceChanges": false,
    "hasUnavailableItems": false,
    "warnings": []
  }
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Debe proporcionar al menos la cantidad o las notas para actualizar el ítem",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T14:05:00",
  "path": "/api/v1/store/burger-bros/cart/items/1a2b3c4d-5e6f-7890-abcd-ef1234567890",
  "method": "PATCH"
}
```

---

#### PATCH `/api/v1/store/{slug}/cart/notes`

> Actualiza las notas generales del carrito (que se heredan automáticamente en el checkout si no se envían notas nuevas).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cliente propietario del carrito  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `slug` (string).

**Body (JSON) — `CartNotesRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `notes` | string | No | Máx. 1000 caracteres | Notas generales del pedido |

**Ejemplo de request:**
```json
{
  "notes": "Tocar el intercomunicador 302"
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<CartResponse>`:**
```json
{
  "success": true,
  "message": "Notas del carrito actualizadas exitosamente",
  "data": {
    "id": "a9b8c7d6-e5f4-3210-abcd-ef1234567890",
    "notes": "Tocar el intercomunicador 302",
    "total": 74.70
  }
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Error de validación: notes: Las notas no pueden exceder 1000 caracteres",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T14:06:00",
  "path": "/api/v1/store/burger-bros/cart/notes",
  "method": "PATCH"
}
```

---

#### DELETE `/api/v1/store/{slug}/cart/items/{itemId}`

> Elimina un ítem específico del carrito y devuelve el carrito actualizado.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cliente propietario del carrito  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `slug` (string), `itemId` (UUID).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<CartResponse>`:**
```json
{
  "success": true,
  "message": "Ítem eliminado del carrito exitosamente",
  "data": {
    "id": "a9b8c7d6-e5f4-3210-abcd-ef1234567890",
    "total": 0.00,
    "items": [],
    "hasPriceChanges": false,
    "hasUnavailableItems": false,
    "warnings": []
  }
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Ítem no encontrado en el carrito",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:07:00",
  "path": "/api/v1/store/burger-bros/cart/items/1a2b3c4d-5e6f-7890-abcd-ef1234567890",
  "method": "DELETE"
}
```

---

#### DELETE `/api/v1/store/{slug}/cart`

> Vacía todos los ítems del carrito del cliente en la tienda.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cliente propietario del carrito  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `slug` (string).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<CartResponse>`:**
```json
{
  "success": true,
  "message": "Carrito vaciado exitosamente",
  "data": {
    "id": "a9b8c7d6-e5f4-3210-abcd-ef1234567890",
    "total": 0.00,
    "items": [],
    "hasPriceChanges": false,
    "hasUnavailableItems": false,
    "warnings": []
  }
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Restaurante no encontrado con slug: burger-bros",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:08:00",
  "path": "/api/v1/store/burger-bros/cart",
  "method": "DELETE"
}
```

> **📝 Notas para el Frontend (Cart):**
> - Todos los endpoints de carrito devuelven el `CartResponse` completo actualizado, evitando llamadas extra a `GET /cart`.
> - Revisar siempre `hasPriceChanges`, `hasUnavailableItems` y `warnings` antes de habilitar el botón de checkout. Si `hasUnavailableItems === true`, impedir continuar hasta que el cliente retire o ajuste la cantidad del ítem afectado (respetando `productStock`).
> - Al iniciar el checkout con Mercado Pago, el carrito **no se vacía de inmediato**; solo se limpia cuando el pago es aprobado (`APPROVED`).

---

## 6. Order — Pedidos, Checkout, KDS y Reparto

> **🏪 Multi-Tenant:** Este módulo gestiona el ciclo de vida de pedidos por restaurante con numeración secuencial (`ORD-000001`), validación de estado abierto/horario de atención (`isOpen`, `openingTime`–`closingTime`), cobertura geográfica (`deliveryRadiusKm`), snapshot tributario inmutable, consumo y reposición atómica de stock (`SELECT ... FOR UPDATE`), máquina de estados por rol (`OrderStateMachine`), notificaciones automáticas (`NEW_ORDER_RECEIVED` y `ORDER_STATUS_CHANGED`) y acumulación automática de puntos de lealtad al entregar (`ENTREGADO`).

---

### 🌐 Pedidos del Cliente — Storefront (`StorefrontOrderController`)

#### GET `/api/v1/store/{slug}/orders`

> Lista paginada del historial de pedidos del cliente autenticado en la tienda `{slug}`, ordenados de más reciente a más antiguo.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cliente autenticado  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `slug` (string).

**Query Parameters:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `page` | integer | No | Página 0-indexed (default: `0`) |
| `size` | integer | No | Elementos por página (default: `20`) |
| `sort` | string | No | Ordenamiento |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<PageResponse<OrderResponse>>`:**
```json
{
  "success": true,
  "message": "Historial de pedidos obtenido exitosamente",
  "data": {
    "content": [
      {
        "id": "1fa85f64-5717-4562-b3fc-2c963f66afa1",
        "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
        "tenantSlug": "burger-bros",
        "tenantName": "Burger Bros",
        "customerId": "7fa85f64-5717-4562-b3fc-2c963f66afa9",
        "customerUserId": "7fa85f64-5717-4562-b3fc-2c963f66afa9",
        "customerFullName": "Mariana Vega",
        "customerEmail": "mariana@ejemplo.com",
        "customerPhone": "+51999111222",
        "deliveryStaffId": null,
        "deliveryStaffName": null,
        "orderNumber": "ORD-000142",
        "ruc": "20123456789",
        "deliveryType": "DELIVERY",
        "status": "PENDIENTE",
        "deliveryAddress": "Calle Las Begonias 441, San Isidro",
        "deliveryLatitude": -12.093124,
        "deliveryLongitude": -77.026451,
        "estimatedDistanceKm": 3.18,
        "estimatedDeliveryMinutes": 24,
        "estimatedArrivalAt": "2026-11-18T15:24:00Z",
        "etaMessage": "Tu pedido estará en tu casa en ~24 min",
        "dispatchedAt": null,
        "deliveredAt": null,
        "subtotal": 42.20,
        "taxTotal": 7.60,
        "deliveryFee": 5.00,
        "total": 54.80,
        "notes": "Tocar el intercomunicador 302",
        "items": [
          {
            "id": "2fa85f64-5717-4562-b3fc-2c963f66afa2",
            "productId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
            "productName": "Hamburguesa Doble Queso",
            "taxId": "11111111-2222-3333-4444-555555555555",
            "taxRate": 18.00,
            "taxAmount": 7.60,
            "unitPrice": 21.10,
            "quantity": 2,
            "subtotal": 42.20,
            "notes": "Sin cebolla",
            "createdAt": "2026-11-18T14:45:00Z",
            "updatedAt": "2026-11-18T14:45:00Z"
          }
        ],
        "createdAt": "2026-11-18T14:45:00Z",
        "updatedAt": "2026-11-18T14:45:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No autorizado: token inválido, expirado o ausente",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-11-18T14:45:00",
  "path": "/api/v1/store/burger-bros/orders",
  "method": null
}
```

---

#### GET `/api/v1/store/{slug}/orders/{id}`

> Obtiene el detalle completo de un pedido perteneciente al cliente autenticado en la tienda `{slug}`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cliente dueño de la orden  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `slug` (string), `id` (UUID de la orden).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<OrderResponse>`:** Retorna el objeto `OrderResponse` completo con sus ítems y ETA dinámico.

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Orden no encontrada",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:45:00",
  "path": "/api/v1/store/burger-bros/orders/1fa85f64-5717-4562-b3fc-2c963f66afa1",
  "method": "GET"
}
```

---

#### POST `/api/v1/store/{slug}/orders/checkout`

> Inicia el checkout del carrito activo del cliente en Mercado Pago (Orders API v1 / Checkout Pro). Valida que el restaurante esté activo (`isActive = true`), **abierto** (`isOpen != false` y dentro de `openingTime`–`closingTime` cuando tiene horario configurado; si está cerrado responde `400 Bad Request`), suscripción vigente, estado no bloqueado del cliente, cobertura geográfica (`DELIVERY`), disponibilidad y stock suficiente. Congela un borrador (`OrderDraft`) en `Payment` y retorna `checkoutUrl`.
>
> **⚠️ Importante:** La orden (`Order`) NO se persiste ni se vacía el carrito en esta llamada; ambos se ejecutan atómicamente al confirmarse el pago (`APPROVED`) vía webhook o reconciliación. Al persistirse la orden, se consume stock (disparando alertas `LOW_STOCK`/`OUT_OF_STOCK` si corresponde) y se envía automáticamente la notificación `NEW_ORDER_RECEIVED` a `OWNER`, `ADMIN` y `COCINERO`. Si el stock se agota durante el pago, se emite reembolso automático.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cliente autenticado no bloqueado con carrito no vacío  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `slug` (string).

**Body (JSON) — `OrderCheckoutRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `deliveryType` | string | Sí | `DELIVERY`, `TAKEAWAY`, `DINE_IN` | Modalidad de entrega |
| `customerAddressId` | UUID | No | Dirección propia del usuario | Dirección guardada (para `DELIVERY`) |
| `deliveryAddress` | string | No | Máx. 255 caracteres | Dirección en texto (respaldo si no usa `customerAddressId`) |
| `deliveryLatitude` | Double | No | `-90.0` a `90.0` | Latitud de entrega (para `DELIVERY`) |
| `deliveryLongitude` | Double | No | `-180.0` a `180.0` | Longitud de entrega (para `DELIVERY`) |
| `ruc` | string | No | Máx. 20 caracteres | RUC opcional para facturación |
| `notes` | string | No | Máx. 1000 caracteres | Notas del pedido (si se omite, hereda `cart.notes`) |

**Ejemplo de request:**
```json
{
  "deliveryType": "DELIVERY",
  "customerAddressId": "d2b3c4d5-e6f7-8901-bcde-fa2345678901",
  "ruc": "20123456789",
  "notes": "Tocar el intercomunicador 302"
}
```

##### 📤 Response

**Éxito (`201 Created`) — Envelope `ApiResponse<CheckoutPaymentResponse>`:**
```json
{
  "success": true,
  "message": "Orden de pago generada exitosamente en Mercado Pago",
  "data": {
    "paymentId": "7fa85f64-5717-4562-b3fc-2c963f66afa7",
    "mpOrderId": "ORD01HY78ZX90ABCD12345678",
    "checkoutUrl": "https://www.mercadopago.com.pe/checkout/v1/redirect?pref_id=12345678-abc-987",
    "status": "PENDING",
    "amount": 54.80,
    "currency": "PEN",
    "expiresAt": "2026-11-18T15:15:00Z"
  }
}
```

**Error (`400` | `401` | `403` | `404` | `409` | `502`) — Envelope `ErrorResponse`:**
```json
{
  "message": "La dirección de entrega se encuentra fuera del radio de cobertura del restaurante (6.50 km)",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T14:45:00",
  "path": "/api/v1/store/burger-bros/orders/checkout",
  "method": "POST"
}
```

---

#### POST `/api/v1/store/{slug}/orders/{id}/cancel`

> Cancela una orden propia del cliente, ejecuta el reembolso total inmediato en Mercado Pago, restaura el stock de todos los productos y envía una notificación `ORDER_STATUS_CHANGED` al cliente. **Solo permitido cuando la orden está en estado `PENDIENTE`**; en cualquier otro estado responde `409 Conflict`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cliente dueño de la orden  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `slug` (string), `id` (UUID de la orden).

**Body (JSON — Opcional) — `OrderCancelRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `reason` | string | No | Máx. 500 caracteres | Motivo de cancelación (default: `"Cancelado por el cliente en estado PENDIENTE"`) |

**Ejemplo de request:**
```json
{
  "reason": "Me equivoqué de dirección de entrega"
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<OrderResponse>`:**
```json
{
  "success": true,
  "message": "Pedido cancelado y reembolsado exitosamente",
  "data": {
    "id": "1fa85f64-5717-4562-b3fc-2c963f66afa1",
    "orderNumber": "ORD-000142",
    "status": "CANCELADO",
    "total": 54.80,
    "etaMessage": "Tu pedido fue cancelado",
    "notes": "Tocar el intercomunicador 302"
  }
}
```

**Error (`401` | `403` | `404` | `409` | `502`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Solo puedes cancelar pedidos en estado PENDIENTE",
  "error": "CONFLICT",
  "status": 409,
  "timestamp": "2026-11-18T14:50:00",
  "path": "/api/v1/store/burger-bros/orders/1fa85f64-5717-4562-b3fc-2c963f66afa1/cancel",
  "method": "POST"
}
```

---

### 🛡️ Pedidos Backoffice, KDS y Reparto (`OrderController`)

#### GET `/api/v1/tenants/{tenantId}/orders`

> Lista paginada de todas las órdenes del restaurante con filtros opcionales por estado, tipo de entrega y rango de fechas.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante (`OWNER`, `ADMIN`, `COCINERO`, `REPARTIDOR`) o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID).

**Query Parameters:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `status` | string (`OrderStatus`) | No | `PENDIENTE`, `EN_PREPARACION`, `LISTO_PARA_ENTREGA`, `EN_CAMINO`, `ENTREGADO`, `CANCELADO` |
| `deliveryType` | string (`DeliveryType`) | No | `DELIVERY`, `TAKEAWAY`, `DINE_IN` |
| `fromDate` | string (ISO 8601) | No | Fecha/hora mínima (`createdAt >= fromDate`, ej. `2026-11-01T00:00:00Z`) |
| `toDate` | string (ISO 8601) | No | Fecha/hora máxima (`createdAt <= toDate`, ej. `2026-11-30T23:59:59Z`) |
| `page` | integer | No | Página 0-indexed (default: `0`) |
| `size` | integer | No | Elementos por página (default: `20`) |
| `sort` | string | No | Ordenamiento |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<PageResponse<OrderResponse>>`:**
```json
{
  "success": true,
  "message": "Órdenes del restaurante obtenidas exitosamente",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "first": true,
    "last": true
  }
}
```

**Error (`400` | `401` | `403`) — Envelope `ErrorResponse`:**
```json
{
  "message": "El parámetro 'status' tiene un formato inválido",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T14:50:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/orders",
  "method": "GET"
}
```

---

#### GET `/api/v1/tenants/{tenantId}/orders/kds`

> Retorna la cola de cocina (Kitchen Display System): todas las órdenes en estado `EN_PREPARACION` ordenadas FIFO (`createdAt ASC`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN`, `COCINERO` o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<List<OrderResponse>>`:**
```json
{
  "success": true,
  "message": "Cola de cocina (KDS) obtenida exitosamente",
  "data": []
}
```

**Error (`401` | `403`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Acceso denegado: no tiene permisos para realizar esta operación",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-18T14:50:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/orders/kds",
  "method": null
}
```

---

#### GET `/api/v1/tenants/{tenantId}/orders/my-deliveries`

> Lista las órdenes asignadas como repartidor al usuario autenticado dentro del restaurante, ordenadas por `createdAt ASC`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN`, `REPARTIDOR` o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID).

**Query Parameters:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `status` | string (`OrderStatus`) | No | Filtra por estado de la orden (ej. `LISTO_PARA_ENTREGA`, `EN_CAMINO`) |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<List<OrderResponse>>`:**
```json
{
  "success": true,
  "message": "Órdenes asignadas de reparto obtenidas exitosamente",
  "data": []
}
```

**Error (`401` | `403`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Acceso denegado: no tiene permisos para realizar esta operación",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-18T14:50:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/orders/my-deliveries",
  "method": null
}
```

---

#### GET `/api/v1/tenants/{tenantId}/orders/{id}`

> Obtiene el detalle completo de una orden específica del restaurante.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `id` (UUID de la orden).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<OrderResponse>`:**
```json
{
  "success": true,
  "message": "Orden obtenida exitosamente",
  "data": {
    "id": "1fa85f64-5717-4562-b3fc-2c963f66afa1",
    "orderNumber": "ORD-000142",
    "status": "EN_PREPARACION",
    "total": 54.80
  }
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Orden no encontrada",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:50:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/orders/1fa85f64-5717-4562-b3fc-2c963f66afa1",
  "method": "GET"
}
```

---

#### PATCH `/api/v1/tenants/{tenantId}/orders/{id}/status`

> Cambia el estado de la orden aplicando bloqueo pesimista, reglas de `OrderStateMachine` por rol y efectos colaterales automáticos:
> - `PENDIENTE -> EN_PREPARACION`: exige pago `APPROVED` (`PaymentGuard`, si no `409 Conflict`).
> - `-> EN_CAMINO`: registra `dispatchedAt`, calcula ETA e inicializa `DeliveryTracking`.
> - `-> ENTREGADO`: registra `deliveredAt`, cierra tracking y suma puntos de lealtad (`max(1, floor(total))`).
> - `-> CANCELADO`: ejecuta reembolso en Mercado Pago, restaura stock y cierra tracking.
> - **Notificación al cliente:** Toda transición exitosa envía automáticamente `ORDER_STATUS_CHANGED` al usuario cliente de la orden.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro del restaurante autorizado para la transición (`OrderStateMachine`) o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `id` (UUID de la orden).

**Body (JSON) — `OrderStatusUpdateRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `status` | string (`OrderStatus`) | Sí | `@NotNull` | Nuevo estado destino |

**Ejemplo de request:**
```json
{
  "status": "EN_PREPARACION"
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<OrderResponse>`:**
```json
{
  "success": true,
  "message": "Estado de la orden actualizado exitosamente",
  "data": {
    "id": "1fa85f64-5717-4562-b3fc-2c963f66afa1",
    "orderNumber": "ORD-000142",
    "status": "EN_PREPARACION",
    "total": 54.80
  }
}
```

**Error (`400` | `401` | `403` | `404` | `409` | `502`) — Envelope `ErrorResponse`:**
```json
{
  "message": "La orden no puede pasar a EN_PREPARACION porque no tiene un pago APPROVED",
  "error": "CONFLICT",
  "status": 409,
  "timestamp": "2026-11-18T14:52:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/orders/1fa85f64-5717-4562-b3fc-2c963f66afa1/status",
  "method": "PATCH"
}
```

---

#### PATCH `/api/v1/tenants/{tenantId}/orders/{id}/assign`

> Asigna un repartidor activo (`REPARTIDOR` del restaurante) a una orden `DELIVERY` que no esté en estado terminal (`ENTREGADO` o `CANCELADO`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `id` (UUID de la orden).

**Body (JSON) — `OrderAssignStaffRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `deliveryStaffId` | UUID | Sí | `@NotNull` | `userId` del miembro activo con rol `REPARTIDOR` |

**Ejemplo de request:**
```json
{
  "deliveryStaffId": "5fa85f64-5717-4562-b3fc-2c963f66afa8"
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<OrderResponse>`:**
```json
{
  "success": true,
  "message": "Repartidor asignado exitosamente a la orden",
  "data": {
    "id": "1fa85f64-5717-4562-b3fc-2c963f66afa1",
    "orderNumber": "ORD-000142",
    "deliveryStaffId": "5fa85f64-5717-4562-b3fc-2c963f66afa8",
    "deliveryStaffName": "Pedro Repartidor",
    "status": "LISTO_PARA_ENTREGA"
  }
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Solo se puede asignar repartidor a pedidos de tipo DELIVERY",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T14:55:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/orders/1fa85f64-5717-4562-b3fc-2c963f66afa1/assign",
  "method": "PATCH"
}
```

---

### 🔄 Máquina de Estados de Pedidos (`OrderStateMachine`)

| Estado Actual | Transiciones Permitidas | Restricciones |
| --- | --- | --- |
| `PENDIENTE` | `EN_PREPARACION`, `CANCELADO` | `EN_PREPARACION` requiere pago `APPROVED` (`PaymentGuard`). |
| `EN_PREPARACION` | `LISTO_PARA_ENTREGA`, `CANCELADO` | — |
| `LISTO_PARA_ENTREGA` | `EN_CAMINO` *(solo `DELIVERY`)*, `ENTREGADO` *(solo `TAKEAWAY`/`DINE_IN`)* | Un pedido `DELIVERY` debe pasar obligatoriamente por `EN_CAMINO`. |
| `EN_CAMINO` | `ENTREGADO` | Solo aplica a `DELIVERY`. |
| `ENTREGADO` | *(ninguna — estado terminal)* | — |
| `CANCELADO` | *(ninguna — estado terminal)* | — |

**Permisos por Rol en `PATCH /status`:**
- `SUPERADMIN`, `OWNER`, `ADMIN`: Cualquier transición válida de la tabla.
- `COCINERO`: Solo destinos `EN_PREPARACION` y `LISTO_PARA_ENTREGA`.
- `REPARTIDOR`: Solo destinos `EN_CAMINO` y `ENTREGADO` (y únicamente en órdenes donde es el `deliveryStaff` asignado, si ya existe uno).

> **📝 Notas para el Frontend (Order):**
> - Todos los IDs son **UUID** y las fechas llegan en **ISO 8601**.
> - Antes de habilitar el botón de checkout, validar que el restaurante esté abierto (`isOpen === true` en `TenantResponse` / `StorefrontMenuResponse`); si el restaurante cierra o queda fuera de horario antes de confirmar, `POST /checkout` responderá `400 Bad Request` con `"El restaurante '<nombre>' se encuentra cerrado en este momento"`.
> - En `POST /checkout`, redirigir al usuario a `data.checkoutUrl` y guardar `data.paymentId` para consultar `GET /api/v1/store/{slug}/payments/{paymentId}` al regresar a `/store/{slug}/checkout/result`.
> - Mostrar el botón de cancelar pedido al cliente **únicamente** cuando `order.status === "PENDIENTE"`.
> - Las cancelaciones desde backoffice solo están habilitadas en `PENDIENTE` o `EN_PREPARACION`.

---

## 7. Payment — Mercado Pago (OAuth, Pagos y Webhooks)

> **🏪 Multi-Tenant:** Cada restaurante vincula su propia cuenta de Mercado Pago vía OAuth 2.0 (tokens cifrados con AES-256-GCM y renovados automáticamente por scheduler antes de expirar). El 100% de los cobros del storefront ingresa directo a la cuenta del restaurante. Un scheduler (`PaymentReconciliationScheduler`) reconcilia cada 5 minutos pagos y suscripciones pendientes.

---

### 🔗 Conexión OAuth del Restaurante (`MercadoPagoConnectionController` & `MercadoPagoOAuthCallbackController`)

#### GET `/api/v1/tenants/{tenantId}/mercadopago/connect`

> Genera la URL de autorización OAuth de Mercado Pago con un `state` firmado mediante HMAC-SHA256 válido por **10 minutos** (`600` segundos) para prevenir CSRF.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<MercadoPagoConnectResponse>`:**
```json
{
  "success": true,
  "message": "URL de autorización de Mercado Pago generada exitosamente",
  "data": {
    "authorizationUrl": "https://auth.mercadopago.com.pe/authorization?client_id=1234567890&response_type=code&platform_id=mp&state=...&redirect_uri=...",
    "expiresInSeconds": 600
  }
}
```

**Error (`400` | `401` | `403`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Acceso denegado: no tiene permisos para realizar esta operación",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/mercadopago/connect",
  "method": null
}
```

---

#### GET `/api/v1/tenants/{tenantId}/mercadopago/status`

> Consulta si el restaurante tiene su cuenta de Mercado Pago vinculada, el `mpUserId` y la fecha de expiración del token OAuth.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<MercadoPagoStatusResponse>`:**
```json
{
  "success": true,
  "message": "Estado de conexión de Mercado Pago obtenido exitosamente",
  "data": {
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "connected": true,
    "mpUserId": "123456789",
    "connectedAt": "2026-11-10T22:15:00Z",
    "tokenExpiresAt": "2027-05-09T22:15:00Z"
  }
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Restaurante no encontrado",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/mercadopago/status",
  "method": "GET"
}
```

---

#### DELETE `/api/v1/tenants/{tenantId}/mercadopago`

> Desvincula la cuenta de Mercado Pago del restaurante eliminando las credenciales OAuth cifradas almacenadas y registrando el evento de auditoría.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<Void>`:**
```json
{
  "success": true,
  "message": "Cuenta de Mercado Pago desvinculada exitosamente",
  "data": null
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Acceso denegado: no tiene permisos para realizar esta operación",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/mercadopago",
  "method": null
}
```

---

#### GET `/api/v1/mercadopago/oauth/callback`

> Endpoint público de redirección registrado en Mercado Pago. Valida el `state` firmado, intercambia `code` por los tokens OAuth del vendedor y guarda las credenciales cifradas.
> - **Navegador (`Accept` contiene `text/html`):** Responde `302 Found` hacia `{FRONTEND_URL}/dashboard/settings/payments?mp=connected` (o `?mp=error&reason=...`).
> - **Cliente API (sin `text/html`):** Responde `200 OK` con `ApiResponse<Map<String, Object>>`.

**Autenticación:** No requerida *(Público; validado mediante `state` firmado HMAC)*  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json` o redirección `302 Found`

##### 📥 Request

**Query Parameters:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `code` | string | No | Código temporal de autorización devuelto por Mercado Pago |
| `state` | string | No | Token firmado HMAC emitido por `/connect` |
| `error` | string | No | Código de error si el usuario rechazó la autorización |

##### 📤 Response

**Éxito Cliente API (`200 OK`) — Envelope `ApiResponse<Map<String, Object>>`:**
```json
{
  "success": true,
  "message": "Cuenta de Mercado Pago vinculada exitosamente",
  "data": {
    "connected": true,
    "redirectUrl": "http://localhost:5173/dashboard/settings/payments?mp=connected"
  }
}
```

**Fallo Cliente API (`200 OK` con `success: false`):**
```json
{
  "success": false,
  "message": "No se pudo completar la vinculación con Mercado Pago",
  "data": {
    "connected": false,
    "redirectUrl": "http://localhost:5173/dashboard/settings/payments?mp=error&reason=invalid_state"
  }
}
```

*(Valores posibles de `reason`: `access_denied`, `missing_params`, `invalid_state`, `tenant_not_found`, `invalid_token_response`, `oauth_exchange_failed`.)*

---

### 💳 Consulta de Estado de Pago — Storefront (`StorefrontPaymentController`)

#### GET `/api/v1/store/{slug}/payments/{paymentId}`

> Permite al comensal consultar el estado de su pago tras retornar de Mercado Pago Checkout Pro. Cuando el pago pasa a `APPROVED`, entrega el `orderId` y `orderNumber` del pedido creado.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cliente propietario del pago  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `slug` (string), `paymentId` (UUID).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<PaymentStatusResponse>`:**
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
    "amount": 54.80,
    "currency": "PEN",
    "approvedAt": "2026-11-18T14:46:00Z",
    "refundedAt": null
  }
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Pago no encontrado",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:46:00",
  "path": "/api/v1/store/burger-bros/payments/7fa85f64-5717-4562-b3fc-2c963f66afa7",
  "method": "GET"
}
```

---

### 🛡️ Auditoría de Pagos — Backoffice (`TenantPaymentController`)

#### GET `/api/v1/tenants/{tenantId}/payments`

> Lista paginada de todas las transacciones e intentos de pago del restaurante ordenados por fecha de creación descendente.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID).

**Query Parameters:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `status` | string (`PaymentStatus`) | No | `PENDING`, `IN_PROCESS`, `APPROVED`, `REJECTED`, `CANCELED`, `EXPIRED`, `REFUND_PENDING`, `REFUNDED`, `CHARGED_BACK` |
| `page` | integer | No | Página 0-indexed (default: `0`) |
| `size` | integer | No | Elementos por página (default: `20`) |
| `sort` | string | No | Ordenamiento |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<PageResponse<PaymentResponse>>`:**
```json
{
  "success": true,
  "message": "Pagos del restaurante obtenidos exitosamente",
  "data": {
    "content": [
      {
        "id": "7fa85f64-5717-4562-b3fc-2c963f66afa7",
        "orderId": "1fa85f64-5717-4562-b3fc-2c963f66afa1",
        "orderNumber": "ORD-000142",
        "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
        "customerId": "7fa85f64-5717-4562-b3fc-2c963f66afa9",
        "mpOrderId": "ORD01HY78ZX90ABCD12345678",
        "mpPaymentId": "9988776655",
        "paymentMethod": "visa",
        "status": "APPROVED",
        "statusDetail": "accredited",
        "amount": 54.80,
        "currency": "PEN",
        "expiresAt": "2026-11-18T15:15:00Z",
        "approvedAt": "2026-11-18T14:46:00Z",
        "refundedAt": null,
        "refundReason": null,
        "createdAt": "2026-11-18T14:45:00Z",
        "updatedAt": "2026-11-18T14:46:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

**Error (`400` | `401` | `403`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Acceso denegado: no tiene permisos para realizar esta operación",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-18T14:46:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/payments",
  "method": null
}
```

---

#### GET `/api/v1/tenants/{tenantId}/orders/{orderId}/payment`

> Obtiene el registro de pago de Mercado Pago asociado a una orden específica del restaurante.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `orderId` (UUID).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<PaymentResponse>`:**
```json
{
  "success": true,
  "message": "Pago de la orden obtenido exitosamente",
  "data": {
    "id": "7fa85f64-5717-4562-b3fc-2c963f66afa7",
    "orderId": "1fa85f64-5717-4562-b3fc-2c963f66afa1",
    "orderNumber": "ORD-000142",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "customerId": "7fa85f64-5717-4562-b3fc-2c963f66afa9",
    "mpOrderId": "ORD01HY78ZX90ABCD12345678",
    "mpPaymentId": "9988776655",
    "paymentMethod": "visa",
    "status": "APPROVED",
    "statusDetail": "accredited",
    "amount": 54.80,
    "currency": "PEN",
    "expiresAt": "2026-11-18T15:15:00Z",
    "approvedAt": "2026-11-18T14:46:00Z",
    "refundedAt": null,
    "refundReason": null,
    "createdAt": "2026-11-18T14:45:00Z",
    "updatedAt": "2026-11-18T14:46:00Z"
  }
}
```

**Error (`401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Pago no encontrado para la orden especificada",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T14:46:00",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/orders/1fa85f64-5717-4562-b3fc-2c963f66afa1/payment",
  "method": "GET"
}
```

---

### 🔔 Webhooks de Mercado Pago (`MercadoPagoWebhookController`)

> **⚠️ Nota:** Estos endpoints son invocados directamente por los servidores de Mercado Pago y **NO** utilizan el envelope `ApiResponse<T>`.

#### GET `/api/v1/webhooks/mercadopago`

> Endpoint de verificación/salud utilizado por Mercado Pago al validar la URL de webhook.

**Autenticación:** No requerida (Público)  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

##### 📤 Response

**Éxito (`200 OK`) — JSON directo:**
```json
{
  "status": "ok",
  "message": "Mercado Pago webhook endpoint active"
}
```

---

#### POST `/api/v1/webhooks/mercadopago`

> Recibe notificaciones asíncronas de Mercado Pago (`order`, `merchant_order`, `subscription_preapproval`, `subscription_authorized_payment`). Valida obligatoriamente la firma HMAC-SHA256 de la cabecera `x-signature` con el manifiesto `id:{dataId};request-id:{x-request-id};ts:{ts};`. Si la firma es inválida, responde `401 Unauthorized` con cuerpo vacío.

**Autenticación:** Firma criptográfica HMAC-SHA256 (`x-signature`, `x-request-id`)  
**Rol requerido:** Servidor de Mercado Pago  
**Content-Type:** `application/json`

##### 📥 Request

**Headers:**
- `x-signature`: `ts=<timestamp>,v1=<hmac_sha256_hex>`
- `x-request-id`: UUID del request de Mercado Pago

**Query Parameters (opcionales):** `data.id`, `id`, `type`, `topic`.

**Body (JSON — opcional) — `MpWebhookNotification`:**
```json
{
  "id": 1234567890,
  "live_mode": false,
  "type": "order",
  "date_created": "2026-11-18T14:46:00Z",
  "user_id": "987654321",
  "api_version": "v1",
  "action": "order.processed",
  "data": {
    "id": "ORD01HY78ZX90ABCD12345678"
  }
}
```

##### 📤 Response

- **Éxito (`200 OK`):** Cuerpo vacío.
- **Firma inválida (`401 Unauthorized`):** Cuerpo vacío.

> **📝 Notas para el Frontend (Payment):**
> - No invocar `/api/v1/webhooks/mercadopago` desde el frontend; es exclusivo para servidor a servidor con Mercado Pago.
> - En la pantalla de retorno del checkout (`/store/{slug}/checkout/result?paymentId=...`), hacer polling cada 2–3 segundos a `GET /api/v1/store/{slug}/payments/{paymentId}` hasta que `status` deje de ser `PENDING`/`IN_PROCESS`.
> - En el panel del restaurante (`/dashboard/settings/payments`), leer los query params `mp=connected` o `mp=error&reason=...` tras regresar del flujo OAuth y refrescar `GET /api/v1/tenants/{tenantId}/mercadopago/status`.
> - Nota sobre enum: el estado de pago cancelado es `CANCELED` (con una sola `L`).

---

## 8. Tracking — Cobertura Geoespacial, ETA y Rastreo GPS en Vivo

> **🏪 Multi-Tenant & PostGIS (WGS84 / SRID 4326):** Calcula cobertura y distancias con `ST_DistanceSphere` (respaldo Haversine en memoria), aplica factor de ruta `roadFactor = 1.35`, velocidad promedio `25.0 km/h`, throttling suave de pings cada `5s`, rechazo de saltos GPS anómalos (`> 150 km/h` en desplazamientos `> 250 m`) y detección de señal obsoleta (`stale = true` tras `60s` sin ping).

---

### 🌐 Estimación y Seguimiento del Comensal (`StorefrontTrackingController`)

#### GET `/api/v1/store/{slug}/delivery-estimate`

> Evalúa si una coordenada de entrega está dentro del `deliveryRadiusKm` del restaurante y calcula distancia lineal, distancia vial estimada, tarifa de envío y rango de tiempo de entrega (`minMinutes`–`maxMinutes`). Si está fuera de cobertura (`withinCoverage: false`), los tiempos llegan como `null`.

**Autenticación:** No requerida (Público)  
**Rol requerido:** Ninguno  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `slug` (string).

**Query Parameters:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `lat` | Double | Sí | Latitud WGS84 del cliente |
| `lng` | Double | Sí | Longitud WGS84 del cliente |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<DeliveryEstimateResponse>`:**
```json
{
  "success": true,
  "message": "Estimación de entrega calculada exitosamente",
  "data": {
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantSlug": "burger-bros",
    "tenantName": "Burger Bros",
    "withinCoverage": true,
    "maxDeliveryRadiusKm": 6.50,
    "straightDistanceKm": 2.45,
    "estimatedRouteDistanceKm": 3.31,
    "deliveryFee": 5.00,
    "estimatedDeliveryMinutes": 13,
    "minMinutes": 11,
    "maxMinutes": 17,
    "estimatedArrivalAt": "2026-11-18T15:13:00Z",
    "message": "Tiempo estimado de entrega: 11-17 min"
  }
}
```

**Error (`400` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "El restaurante no tiene configurada su ubicación geográfica",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T15:00:00",
  "path": "/api/v1/store/burger-bros/delivery-estimate",
  "method": "GET"
}
```

---

#### GET `/api/v1/store/{slug}/orders/{orderId}/tracking`

> Obtiene el seguimiento en vivo de un pedido `DELIVERY` perteneciente al cliente autenticado.
> **Privacidad:** Las coordenadas del repartidor (`driverLatitude`, `driverLongitude`, `heading`, `speedKmh`, `accuracyM`) solo se exponen mientras el pedido está en `EN_CAMINO` con `trackingActive = true`; fuera de ese estado se retornan como `null`. `routeHistory` siempre llega vacío `[]` para clientes.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cliente dueño de la orden  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `slug` (string), `orderId` (UUID).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<DeliveryTrackingResponse>`:**
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
    "deliveryAddress": "Calle Las Begonias 441, San Isidro",
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
    "estimatedArrivalAt": "2026-11-18T15:08:00Z",
    "etaMessage": "Tu pedido estará en tu puerta en ~8 min",
    "trackingActive": true,
    "stale": false,
    "throttled": false,
    "pollIntervalSeconds": 15,
    "lastUpdatedAt": "2026-11-18T15:00:12Z",
    "dispatchedAt": "2026-11-18T14:52:00Z",
    "deliveredAt": null,
    "routeHistory": []
  }
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "El seguimiento GPS solo aplica para pedidos de tipo DELIVERY",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T15:00:12",
  "path": "/api/v1/store/burger-bros/orders/1fa85f64-5717-4562-b3fc-2c963f66afa1/tracking",
  "method": "GET"
}
```

---

### 🏍️ Telemetría del Repartidor y Mapa de Despacho (`DeliveryTrackingController`)

#### GET `/api/v1/tenants/{tenantId}/tracking/active`

> Lista todos los pedidos del restaurante actualmente en ruta (`EN_CAMINO` con `trackingActive = true`), ordenados por última actualización descendente, para el mapa en vivo del panel administrativo.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `OWNER`, `ADMIN` del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID).

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<List<DeliveryTrackingResponse>>`:**
```json
{
  "success": true,
  "message": "Entregas activas en ruta obtenidas exitosamente",
  "data": []
}
```

**Error (`401` | `403`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Acceso denegado: no tiene permisos para realizar esta operación",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-18T15:00:12",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/tracking/active",
  "method": null
}
```

---

#### GET `/api/v1/tenants/{tenantId}/orders/{orderId}/tracking`

> Obtiene el estado de seguimiento de una orden `DELIVERY` para el equipo del restaurante. Si `includeHistory=true`, incluye todos los puntos GPS registrados cronológicamente (`recordedAt ASC`) para dibujar la polilínea recorrida.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Miembro activo del restaurante o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `orderId` (UUID).

**Query Parameters:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `includeHistory` | boolean | No | Si es `true`, incluye `routeHistory` (default: `false`) |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<DeliveryTrackingResponse>`:**
```json
{
  "success": true,
  "message": "Estado de seguimiento de la orden obtenido exitosamente",
  "data": {
    "orderId": "1fa85f64-5717-4562-b3fc-2c963f66afa1",
    "orderNumber": "ORD-000142",
    "orderStatus": "EN_CAMINO",
    "trackingActive": true,
    "stale": false,
    "pollIntervalSeconds": 15,
    "routeHistory": [
      {
        "latitude": -12.115000,
        "longitude": -77.028100,
        "heading": 350.0,
        "speedKmh": 24.0,
        "accuracyM": 5.0,
        "recordedAt": "2026-11-18T14:55:00Z"
      }
    ]
  }
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Orden no encontrada",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-18T15:00:12",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/orders/1fa85f64-5717-4562-b3fc-2c963f66afa1/tracking",
  "method": "GET"
}
```

---

#### PUT `/api/v1/tenants/{tenantId}/orders/{orderId}/tracking/location`

> Envía un ping GPS del repartidor para una orden `DELIVERY` en estado `EN_CAMINO` con repartidor asignado. Recalcula el ETA en vivo y registra el punto en el historial.
> - **Throttling suave:** Si pasan menos de `5` segundos desde el último ping, retorna `200 OK` con la última posición guardada y `throttled = true` sin escribir en BD.
> - **Filtro de saltos:** Rechaza con `400 Bad Request` desplazamientos `> 0.25 km` que impliquen una velocidad superior a `150 km/h`.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** `REPARTIDOR` asignado al pedido, `OWNER`, `ADMIN` o `SUPERADMIN`  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:** `tenantId` (UUID), `orderId` (UUID).

**Body (JSON) — `DriverLocationUpdateRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `latitude` | Double | Sí | `@NotNull`, `-90.0` a `90.0` | Latitud GPS actual |
| `longitude` | Double | Sí | `@NotNull`, `-180.0` a `180.0` | Longitud GPS actual |
| `heading` | Double | No | `0.0` a `360.0` | Rumbo en grados (0° Norte) |
| `speedKmh` | Double | No | `0.0` a `200.0` | Velocidad instantánea (km/h) |
| `accuracyM` | Double | No | `≥ 0.0` | Precisión del sensor GPS (metros) |

**Ejemplo de request:**
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

**Éxito (`200 OK`) — Envelope `ApiResponse<DeliveryTrackingResponse>`:**
```json
{
  "success": true,
  "message": "Ubicación del repartidor actualizada exitosamente",
  "data": {
    "orderId": "1fa85f64-5717-4562-b3fc-2c963f66afa1",
    "orderStatus": "EN_CAMINO",
    "driverLatitude": -12.105432,
    "driverLongitude": -77.027812,
    "remainingDistanceKm": 1.42,
    "etaMinutes": 8,
    "trackingActive": true,
    "stale": false,
    "throttled": false,
    "pollIntervalSeconds": 15,
    "lastUpdatedAt": "2026-11-18T15:00:12Z"
  }
}
```

**Error (`400` | `401` | `403` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Solo el repartidor asignado a la orden puede actualizar su ubicación",
  "error": "FORBIDDEN",
  "status": 403,
  "timestamp": "2026-11-18T15:00:12",
  "path": "/api/v1/tenants/e1a2c3d4-e5f6-7890-abcd-ef1234567890/orders/1fa85f64-5717-4562-b3fc-2c963f66afa1/tracking/location",
  "method": "PUT"
}
```

> **📝 Notas para el Frontend (Tracking):**
> - Las coordenadas geográficas usan **WGS84 (SRID 4326)**: `latitude` y `longitude` como `Double`.
> - Usar `pollIntervalSeconds` (por defecto `15` segundos) para programar el polling en el mapa del cliente y detenerlo automáticamente cuando `trackingActive === false`.
> - Si `stale === true` (>60 segundos sin ping del repartidor), mostrar un indicador visual ("Última ubicación hace más de 1 minuto").
> - La app del repartidor debe enviar pings cada 10–15 segundos; si recibe `throttled: true`, no es un error, simplemente se descartó la escritura por llegar antes de 5 segundos.

---

## 9. Notification — Notificaciones e Invitaciones Proactivas

> **🔔 Notificaciones Cross-Tenant:** A diferencia de los recursos operativos de un restaurante (`Tenant`), las notificaciones pertenecen directamente al usuario (`recipient_user_id`), por lo que el usuario recibe en un único buzón centralizado (`/api/v1/notifications`) tanto sus invitaciones a distintos restaurantes como alertas operativas (stock, horarios, suscripciones) y actualizaciones de pedidos en los que participa.

---

### 📬 Bandeja de Notificaciones (`NotificationController`)

#### GET `/api/v1/notifications`

> Lista de forma paginada las notificaciones del usuario autenticado, ordenadas de más reciente a más antigua (`createdAt DESC`). Permite filtrar por notificaciones no leídas (`unreadOnly`) y por tipo (`type`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado  
**Content-Type:** `application/json`

##### 📥 Request

**Query Parameters:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `unreadOnly` | boolean | No | Si es `true`, retorna únicamente las notificaciones con `isRead = false` |
| `type` | string (`NotificationType`) | No | Filtra por tipo de notificación (ej. `STAFF_INVITATION`, `LOW_STOCK`, `ORDER_STATUS_CHANGED`) |
| `page` | integer | No | Número de página 0-indexed (default: `0`) |
| `size` | integer | No | Elementos por página (default: `20`) |
| `sort` | string | No | Parámetro estándar de paginación Spring |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<PageResponse<NotificationResponse>>`:**
```json
{
  "success": true,
  "message": "Notificaciones obtenidas exitosamente",
  "data": {
    "content": [
      {
        "id": "9a1b2c3d-4e5f-6789-abcd-ef1234567890",
        "recipientUserId": "4fa85f64-5717-4562-b3fc-2c963f66afa7",
        "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
        "tenantName": "Burger Bros",
        "type": "STAFF_INVITATION",
        "title": "Invitación al equipo de Burger Bros",
        "message": "Has sido invitado a unirte al restaurante 'Burger Bros' con el rol COCINERO. Puedes aceptar o rechazar esta invitación.",
        "actionRequired": true,
        "actionStatus": "PENDING",
        "allowedActions": [
          "ACCEPT",
          "REJECT"
        ],
        "referenceId": "a2b3c4d5-e6f7-8901-abcd-ef1234567890",
        "metadata": {
          "invitationId": "a2b3c4d5-e6f7-8901-abcd-ef1234567890",
          "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
          "tenantName": "Burger Bros",
          "role": "COCINERO",
          "allowedActions": [
            "ACCEPT",
            "REJECT"
          ]
        },
        "isRead": false,
        "readAt": null,
        "respondedAt": null,
        "createdAt": "2026-11-22T14:30:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

**Error (`400` | `401`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No autorizado: token inválido, expirado o ausente",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-11-22T14:30:00",
  "path": "/api/v1/notifications",
  "method": null
}
```

---

#### GET `/api/v1/notifications/unread-count`

> Obtiene el conteo total de notificaciones no leídas (`isRead = false`) del usuario autenticado para badges en la barra de navegación.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado  
**Content-Type:** `application/json`

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<UnreadNotificationCountResponse>`:**
```json
{
  "success": true,
  "message": "Contador de notificaciones obtenido exitosamente",
  "data": {
    "unreadCount": 3
  }
}
```

**Error (`401`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No autorizado: token inválido, expirado o ausente",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-11-22T14:30:00",
  "path": "/api/v1/notifications/unread-count",
  "method": null
}
```

---

#### GET `/api/v1/notifications/stream`

> Abre una conexión persistente **Server-Sent Events (SSE)** para recibir notificaciones en tiempo real y sincronizar el contador de no leídas sin recargar la página ni hacer polling.
>
> **Autenticación dual:**
> 1. **Recomendada para `EventSource` nativo:** obtener un ticket opaco de un solo uso con `POST /api/v1/notifications/stream-ticket` y conectar a `/api/v1/notifications/stream?ticket=<ticket>`.
> 2. **Recomendada para `fetch` / `@microsoft/fetch-event-source`:** enviar el header estándar `Authorization: Bearer <accessToken>` sin query params.
>
> **Límites y comportamiento de la conexión:**
> - Máximo `5` conexiones simultáneas por usuario (`app.notifications.sse.max-connections-per-user`); si se abre una adicional, el servidor cierra la más antigua.
> - Timeout por defecto de `30` minutos (`app.notifications.sse.timeout-ms: 1800000`) con `retry: 3000` ms para reconexión automática.
> - Heartbeat periódico cada `25` segundos (`: keep-alive`) para evitar cierres por proxies o balanceadores.
> - Las notificaciones se publican únicamente después del `commit` de la transacción en base de datos.

**Autenticación:** Requerida (`Bearer <token>` o query param `?ticket=<ticket>`)  
**Rol requerido:** Cualquiera autenticado  
**Content-Type (Response):** `text/event-stream` *(con `Cache-Control: no-cache, no-transform` y `X-Accel-Buffering: no`)*

##### 📥 Request

**Query Parameters:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `ticket` | string | No | Ticket temporal de un solo uso obtenido desde `POST /api/v1/notifications/stream-ticket` (alternativa segura al header `Authorization: Bearer` para `EventSource` nativo) |

##### 📤 Response (`200 OK` — Stream `text/event-stream`)

**1. Evento `connected` (enviado inmediatamente al conectar):**
```text
event:connected
retry:3000
data:{"status":"CONNECTED","unreadCount":3,"connectedAt":"2026-11-23T15:30:00Z"}

```

**2. Evento `notification` (enviado al crearse una nueva notificación; payload `NotificationResponse`):**
```text
id:9a1b2c3d-4e5f-6789-abcd-ef1234567890
event:notification
data:{"id":"9a1b2c3d-4e5f-6789-abcd-ef1234567890","recipientUserId":"4fa85f64-5717-4562-b3fc-2c963f66afa7","tenantId":"e1a2c3d4-e5f6-7890-abcd-ef1234567890","tenantName":"Burger Bros","type":"NEW_ORDER_RECEIVED","title":"Nuevo pedido recibido: #ORD-000042","message":"Se ha recibido el pedido #ORD-000042 en 'Burger Bros' por S/ 45.50.","actionRequired":false,"actionStatus":"NONE","allowedActions":[],"referenceId":"1fa85f64-5717-4562-b3fc-2c963f66afa1","metadata":{"orderId":"1fa85f64-5717-4562-b3fc-2c963f66afa1","orderNumber":"ORD-000042","status":"PENDIENTE","deliveryType":"DELIVERY","total":45.50},"isRead":false,"readAt":null,"respondedAt":null,"createdAt":"2026-11-23T15:31:00Z"}

```

**3. Evento `unread-count` (enviado tras nueva notificación, `markAsRead`, `markAllAsRead` o respuesta a invitación; payload `UnreadNotificationCountResponse`):**
```text
event:unread-count
data:{"unreadCount":4}

```

**4. Heartbeat keep-alive (comentario SSE cada 25 segundos, ignorado automáticamente por `EventSource`):**
```text
:keep-alive

```

**Error (`401`) — Envelope `ErrorResponse` (`application/json`):**
```json
{
  "message": "Ticket SSE inválido, expirado o ya utilizado",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-11-23T15:30:00",
  "path": "/api/v1/notifications/stream",
  "method": "GET"
}
```

---

#### POST `/api/v1/notifications/stream-ticket`

> Genera un ticket opaco temporal y de un solo uso (TTL por defecto: `30` segundos) para autenticar el stream `GET /api/v1/notifications/stream?ticket=...` usando el `EventSource` nativo del navegador sin exponer el JWT en la URL ni en logs de proxies.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado  
**Content-Type:** `application/json`

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<NotificationStreamTicketResponse>`:**
```json
{
  "success": true,
  "message": "Ticket SSE generado exitosamente",
  "data": {
    "ticket": "8f2c9a1b4d7e6f0a3c5b8d9e1f2a4c6b7e8d9f0a1b2c",
    "expiresInSeconds": 30,
    "expiresAt": "2026-11-23T15:30:30Z"
  }
}
```

**Error (`401`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No autorizado: token inválido, expirado o ausente",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-11-23T15:30:00",
  "path": "/api/v1/notifications/stream-ticket",
  "method": null
}
```

---

#### PATCH `/api/v1/notifications/{id}/read`

> Marca como leída una notificación perteneciente al usuario autenticado (`isRead = true` y asigna `readAt`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Usuario destinatario de la notificación  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `id` | UUID | Sí | ID de la notificación |

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<NotificationResponse>`:**
```json
{
  "success": true,
  "message": "Notificación marcada como leída",
  "data": {
    "id": "9a1b2c3d-4e5f-6789-abcd-ef1234567890",
    "recipientUserId": "4fa85f64-5717-4562-b3fc-2c963f66afa7",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantName": "Burger Bros",
    "type": "LOW_STOCK",
    "title": "Stock bajo: Hamburguesa Doble Queso",
    "message": "El producto 'Hamburguesa Doble Queso' en 'Burger Bros' tiene stock bajo (3 unidades restantes).",
    "actionRequired": false,
    "actionStatus": "NONE",
    "allowedActions": [],
    "referenceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "metadata": {
      "productId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "productName": "Hamburguesa Doble Queso",
      "stock": 3,
      "threshold": 5
    },
    "isRead": true,
    "readAt": "2026-11-22T14:35:00Z",
    "respondedAt": null,
    "createdAt": "2026-11-22T14:30:00Z"
  }
}
```

**Error (`401` | `404`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Notificación no encontrada",
  "error": "NOT_FOUND",
  "status": 404,
  "timestamp": "2026-11-22T14:35:00",
  "path": "/api/v1/notifications/9a1b2c3d-4e5f-6789-abcd-ef1234567890/read",
  "method": "PATCH"
}
```

---

#### PATCH `/api/v1/notifications/read-all`

> Marca todas las notificaciones no leídas (`isRead = false`) del usuario autenticado como leídas y retorna el número de registros actualizados.

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Cualquiera autenticado  
**Content-Type:** `application/json`

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<Map<String, Integer>>`:**
```json
{
  "success": true,
  "message": "Notificaciones marcadas como leídas",
  "data": {
    "updatedCount": 4
  }
}
```

**Error (`401`) — Envelope `ErrorResponse`:**
```json
{
  "message": "No autorizado: token inválido, expirado o ausente",
  "error": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2026-11-22T14:36:00",
  "path": "/api/v1/notifications/read-all",
  "method": null
}
```

---

#### POST `/api/v1/notifications/{id}/respond` *(alias: `PATCH /api/v1/notifications/{id}/respond`)*

> Responde a una notificación accionable de tipo `STAFF_INVITATION` (`actionRequired = true`, `actionStatus = "PENDING"`). Acepta **únicamente** `decision: "ACCEPT"` o `decision: "REJECT"` (`InvitationActionType`).
> - Al enviar `"ACCEPT"`: delega en `TenantInvitationDecisionService`, crea el `TenantMember` activo en la tienda, actualiza la notificación a `actionStatus = "ACCEPTED"`, `allowedActions = []`, `isRead = true`, `respondedAt = now`, y envía `INVITATION_ACCEPTED` al propietario (`OWNER`).
> - Al enviar `"REJECT"`: rechaza la invitación sin agregar al usuario al staff, actualiza la notificación a `actionStatus = "REJECTED"`, `allowedActions = []`, `isRead = true`, `respondedAt = now`, y envía `INVITATION_REJECTED` al propietario (`OWNER`).

**Autenticación:** Requerida (`Bearer <token>`)  
**Rol requerido:** Usuario destinatario de la notificación  
**Content-Type:** `application/json`

##### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo | Requerido | Descripción |
| --- | --- | --- | --- |
| `id` | UUID | Sí | ID de la notificación `STAFF_INVITATION` |

**Body (JSON) — `NotificationInvitationDecisionRequest`:**

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `decision` | string (`InvitationActionType`) | Sí | `@NotNull("La decisión (ACCEPT o REJECT) es obligatoria")`, valores permitidos: `"ACCEPT"`, `"REJECT"` | Decisión del usuario sobre la invitación |

**Ejemplo de request:**
```json
{
  "decision": "ACCEPT"
}
```

##### 📤 Response

**Éxito (`200 OK`) — Envelope `ApiResponse<NotificationResponse>`:**
```json
{
  "success": true,
  "message": "Invitación aceptada exitosamente",
  "data": {
    "id": "9a1b2c3d-4e5f-6789-abcd-ef1234567890",
    "recipientUserId": "4fa85f64-5717-4562-b3fc-2c963f66afa7",
    "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "tenantName": "Burger Bros",
    "type": "STAFF_INVITATION",
    "title": "Invitación al equipo de Burger Bros",
    "message": "Has sido invitado a unirte al restaurante 'Burger Bros' con el rol COCINERO. Puedes aceptar o rechazar esta invitación.",
    "actionRequired": true,
    "actionStatus": "ACCEPTED",
    "allowedActions": [],
    "referenceId": "a2b3c4d5-e6f7-8901-abcd-ef1234567890",
    "metadata": {
      "invitationId": "a2b3c4d5-e6f7-8901-abcd-ef1234567890",
      "tenantId": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
      "tenantName": "Burger Bros",
      "role": "COCINERO",
      "allowedActions": [
        "ACCEPT",
        "REJECT"
      ]
    },
    "isRead": true,
    "readAt": "2026-11-22T14:40:00Z",
    "respondedAt": "2026-11-22T14:40:00Z",
    "createdAt": "2026-11-22T14:30:00Z"
  }
}
```

*(Si `decision` es `"REJECT"`, `message` será `"Invitación rechazada exitosamente"` y `actionStatus` será `"REJECTED"`.)*

**Error (`400` | `401` | `403` | `404` | `409`) — Envelope `ErrorResponse`:**
```json
{
  "message": "Esta invitación ya fue respondida",
  "error": "CONFLICT",
  "status": 409,
  "timestamp": "2026-11-22T14:41:00",
  "path": "/api/v1/notifications/9a1b2c3d-4e5f-6789-abcd-ef1234567890/respond",
  "method": "POST"
}
```

---

### 🔔 Catálogo de Notificaciones Proactivas y Deduplicación

El sistema genera automáticamente notificaciones ante eventos de negocio y tareas programadas (`@Scheduled`), evitando duplicados por usuario mediante `dedupKey` (`existsByRecipientIdAndDedupKey`):

| `NotificationType` | Disparador | Destinatarios | `actionRequired` | Estrategia de Deduplicación (`dedupKey`) |
| --- | --- | --- | --- | --- |
| `STAFF_INVITATION` | `POST /api/v1/tenants/{tenantId}/members` | Usuario invitado | `true` (`allowedActions: ["ACCEPT", "REJECT"]`) | `STAFF_INVITATION:{invitationId}` |
| `INVITATION_ACCEPTED` | Usuario acepta invitación (`ACCEPT`) | `OWNER` del tenant *(o `inviter`)* | `false` | `INVITATION_ACCEPTED:{invitationId}` |
| `INVITATION_REJECTED` | Usuario rechaza invitación (`REJECT`) | `OWNER` del tenant *(o `inviter`)* | `false` | `INVITATION_REJECTED:{invitationId}` |
| `LOW_STOCK` | `0 < stock <= 5` *(umbral `app.notifications.low-stock-threshold`)* al crear/actualizar/consumir stock o job diario (`08:00`) | `OWNER`, `ADMIN`, `COCINERO` | `false` | `LOW_STOCK:{productId}:{fecha}:{stock}` |
| `OUT_OF_STOCK` | `stock == 0` al crear/actualizar/consumir stock o job diario (`08:00`) | `OWNER`, `ADMIN`, `COCINERO` | `false` | `OUT_OF_STOCK:{productId}:{fecha}` |
| `STORE_OPENING_SOON` | `StoreScheduleScheduler` (cada min): faltan `<= 15` min para `openingTime` | `OWNER`, `ADMIN`, `COCINERO` | `false` | `STORE_OPENING_SOON:{tenantId}:{fecha}:{openingTime}` |
| `STORE_CLOSING_SOON` | `StoreScheduleScheduler` (cada min): faltan `<= 15` min para `closingTime` | `OWNER`, `ADMIN`, `COCINERO` | `false` | `STORE_CLOSING_SOON:{tenantId}:{fecha}:{closingTime}` |
| `STORE_AUTO_CLOSED` | `StoreScheduleScheduler`: pasó `closingTime` y marcó `isOpen = false` | `OWNER`, `ADMIN` | `false` | `STORE_AUTO_CLOSED:{tenantId}:{fecha}:{closingTime}` |
| `STORE_AUTO_OPENED` | `StoreScheduleScheduler`: llegó `openingTime` y marcó `isOpen = true` | `OWNER`, `ADMIN` | `false` | `STORE_AUTO_OPENED:{tenantId}:{fecha}:{openingTime}` |
| `SUBSCRIPTION_EXPIRING_SOON` | `ProactiveNotificationScheduler` (`app.notifications.daily-check-cron`, default `08:00`): suscripción `TRIAL` o `ACTIVE` vence en `<= 3` días | `OWNER` de la tienda *(o titular)* | `false` | `SUB_EXPIRING:{subscriptionId}:{fecha}` |
| `NEW_ORDER_RECEIVED` | Nueva orden persistida (`persistFromDraft` / pago `APPROVED`) | `OWNER`, `ADMIN`, `COCINERO` | `false` | `NEW_ORDER:{orderId}` |
| `ORDER_STATUS_CHANGED` | Cambio de estado de orden o cancelación por cliente | Cliente del pedido (`order.customer.user`) | `false` | `null` *(notifica cada transición)* |

> **📝 Notas para el Frontend (Notification):**
> - Todos los IDs son **UUID** y las fechas (`readAt`, `respondedAt`, `createdAt`) llegan en **ISO 8601**.
> - Mostrar botones de acción en una notificación **únicamente** cuando `notification.actionRequired === true && notification.actionStatus === "PENDING"` y renderizar exactamente las opciones listadas en `notification.allowedActions` (`["ACCEPT", "REJECT"]`).
> - Al responder la notificación (`POST /api/v1/notifications/{id}/respond`) o la invitación (`POST /api/v1/invitations/{invitationId}/respond`), ambos estados quedan sincronizados automáticamente y el servidor devuelve `allowedActions: []`.
> - **Tiempo real con SSE (sin polling):** al iniciar sesión, solicitar un ticket con `POST /api/v1/notifications/stream-ticket` y abrir `new EventSource(`${API_URL}/notifications/stream?ticket=${ticket}`)` (o usar `@microsoft/fetch-event-source` con `Authorization: Bearer <accessToken>`). Escuchar los eventos `connected`, `notification` (insertar al inicio de la lista / mostrar toast) y `unread-count` (actualizar el badge y sincronizar múltiples pestañas).
> - **Reconexión con `EventSource` nativo:** como el `ticket` es de un solo uso, ante `onerror` cerrar el `EventSource` actual, pedir un nuevo ticket vía `POST /api/v1/notifications/stream-ticket` (renovando el access token si expiró) y reconectar. Al reconectar, el evento `connected` entrega el `unreadCount` actualizado y se puede refrescar `GET /api/v1/notifications` si se desea recuperar notificaciones perdidas durante la desconexión.

---

# 📦 Modelos de Datos / DTOs

> **📝 Nota sobre IDs:** Todos los IDs en NexoFood son de tipo **UUID** (formato: `3fa85f64-5717-4562-b3fc-2c963f66afa6`). El frontend debe tratarlos siempre como `string`.
>
> **📝 Nota sobre fechas:** Todas las fechas se serializan en formato **ISO 8601** (ej: `2026-11-18T14:00:00Z`), nunca como timestamps numéricos.

---

## Enums del Sistema

| Enum | Valores |
| --- | --- |
| `UserSystemRole` | `SUPERADMIN`, `USER` |
| `SubscriptionStatus` | `PENDING`, `TRIAL`, `ACTIVE`, `PAST_DUE`, `CANCELED`, `EXPIRED` |
| `SubscriptionPaymentStatus` | `PENDING`, `APPROVED`, `REJECTED`, `REFUNDED` |
| `TenantStaffRole` | `OWNER`, `ADMIN`, `COCINERO`, `REPARTIDOR` |
| `TenantInvitationStatus` | `PENDING`, `ACCEPTED`, `REJECTED` |
| `DeliveryType` | `DELIVERY`, `TAKEAWAY`, `DINE_IN` |
| `OrderStatus` | `PENDIENTE`, `EN_PREPARACION`, `LISTO_PARA_ENTREGA`, `EN_CAMINO`, `ENTREGADO`, `CANCELADO` |
| `PaymentStatus` | `PENDING`, `IN_PROCESS`, `APPROVED`, `REJECTED`, `CANCELED`, `EXPIRED`, `REFUND_PENDING`, `REFUNDED`, `CHARGED_BACK` |
| `NotificationType` | `STAFF_INVITATION`, `INVITATION_ACCEPTED`, `INVITATION_REJECTED`, `LOW_STOCK`, `OUT_OF_STOCK`, `STORE_OPENING_SOON`, `STORE_CLOSING_SOON`, `STORE_AUTO_CLOSED`, `STORE_AUTO_OPENED`, `SUBSCRIPTION_EXPIRING_SOON`, `NEW_ORDER_RECEIVED`, `ORDER_STATUS_CHANGED` |
| `NotificationActionStatus` | `NONE`, `PENDING`, `ACCEPTED`, `REJECTED` |
| `InvitationActionType` | `ACCEPT`, `REJECT` |

---

## DTOs — 1. Identity

### 📥 DTO: UserRegisterRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `email` | string | Sí | Email válido, máx. 255 | Correo electrónico |
| `password` | string | Sí | 8–100 chars, ≥1 letra, ≥1 dígito, sin espacios | Contraseña |
| `fullName` | string | Sí | No vacío, máx. 150 | Nombre completo |
| `phone` | string | No | Máx. 20 | Teléfono de contacto |

### 📥 DTO: LoginRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `email` | string | Sí | Email válido, máx. 255 | Correo electrónico |
| `password` | string | Sí | No vacío, máx. 100 | Contraseña |

### 📥 DTO: RefreshTokenRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `refreshToken` | string | Sí | No vacío | Token de refresco |

### 📥 DTO: UserUpdateRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `fullName` | string | Sí | No vacío, máx. 150 | Nombre completo |
| `phone` | string | No | Máx. 20 | Teléfono de contacto |

### 📥 DTO: ChangePasswordRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `currentPassword` | string | Sí | No vacío, máx. 100 | Contraseña actual |
| `newPassword` | string | Sí | 8–100 chars, política de contraseñas, ≠ actual | Nueva contraseña |

### 📥 DTO: CustomerAddressRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `title` | string | No | Máx. 50 (default `"Casa"` al crear) | Etiqueta de dirección |
| `addressLine` | string | Sí | No vacío, máx. 255 | Dirección completa |
| `reference` | string | No | Máx. 500 | Referencia adicional |
| `latitude` | Double | Sí | `-90.0` a `90.0` | Latitud WGS84 |
| `longitude` | Double | Sí | `-180.0` a `180.0` | Longitud WGS84 |

### 📥 DTO: UserStatusUpdateRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `isActive` | boolean | Sí | `@NotNull` | Estado activo de la cuenta |

### 📤 DTO: AuthResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `accessToken` | string | No | JWT de acceso (15 min) |
| `refreshToken` | string | No | JWT de refresco (7 días) |
| `tokenType` | string | No | Siempre `"Bearer"` |
| `expiresIn` | number (long) | No | Segundos hasta expiración del access token (`900`) |
| `user` | `UserResponse` | No | Datos del usuario autenticado |
| `staffMemberships` | `List<TenantStaffMembershipDto>` | Sí | Poblado en `/login`; `null` en `/register` y `/refresh` |

### 📤 DTO: UserResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | Identificador único |
| `email` | string | No | Correo electrónico |
| `fullName` | string | No | Nombre completo |
| `phone` | string | Sí | Teléfono |
| `systemRole` | `UserSystemRole` | No | `"SUPERADMIN"` o `"USER"` |
| `isActive` | boolean | No | Si la cuenta está activa |
| `createdAt` | string | No | Fecha de creación (ISO 8601) |
| `updatedAt` | string | No | Fecha de actualización (ISO 8601) |

### 📤 DTO: UserProfileResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | Identificador único |
| `email` | string | No | Correo electrónico |
| `fullName` | string | No | Nombre completo |
| `phone` | string | Sí | Teléfono |
| `systemRole` | string | No | `"SUPERADMIN"` o `"USER"` |
| `isActive` | boolean | No | Si la cuenta está activa |
| `staffMemberships` | `List<TenantStaffMembershipDto>` | No | Membresías de staff del usuario |
| `addresses` | `List<CustomerAddressResponse>` | No | Direcciones guardadas |

### 📤 DTO: TenantStaffMembershipDto

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `tenantId` | UUID (string) | No | ID del restaurante |
| `tenantName` | string | No | Nombre del restaurante |
| `tenantSlug` | string | No | Slug del restaurante |
| `staffRole` | `TenantStaffRole` | No | Rol del usuario en ese restaurante |

### 📤 DTO: CustomerAddressResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID de la dirección |
| `userId` | UUID (string) | No | ID del usuario propietario |
| `title` | string | Sí | Etiqueta |
| `addressLine` | string | No | Dirección completa |
| `reference` | string | Sí | Referencia |
| `latitude` | Double | No | Latitud WGS84 |
| `longitude` | Double | No | Longitud WGS84 |
| `createdAt` | string | No | ISO 8601 |
| `updatedAt` | string | No | ISO 8601 |

---

## DTOs — 2. Subscription

### 📥 DTO: SubscriptionPlanRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `name` | string | Sí | No vacío, máx. 100 | Nombre comercial del plan |
| `price` | BigDecimal | Sí | `0.00` a `999999.99` | Precio por ciclo |
| `billingCycleDays` | integer | Sí | `1` a `3650` | Duración del ciclo en días |
| `maxProducts` | integer | No | `1` a `1000000` | Límite de productos (`null` = ilimitado) |
| `isActive` | boolean | No | Default: `true` | Estado visible del plan |

### 📥 DTO: SubscriptionPlanStatusRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `isActive` | boolean | Sí | `@NotNull` | Nuevo estado del plan |

### 📥 DTO: SubscriptionCreateRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `planId` | UUID | Sí | `@NotNull` | ID del plan SaaS elegido |

### 📤 DTO: SubscriptionPlanResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID del plan |
| `name` | string | No | Nombre del plan |
| `price` | BigDecimal | No | Precio del plan |
| `billingCycleDays` | integer | No | Días de vigencia por ciclo |
| `maxProducts` | integer | Sí | Límite máximo de productos (`null` = ilimitado) |
| `isActive` | boolean | No | Si está disponible para contratación |
| `createdAt` | string | No | ISO 8601 |
| `updatedAt` | string | No | ISO 8601 |

### 📤 DTO: SubscriptionResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID de la suscripción |
| `plan` | `SubscriptionPlanResponse` | No | Plan contratado |
| `userId` | UUID (string) | No | Propietario |
| `status` | `SubscriptionStatus` | No | Estado de la suscripción |
| `startDate` | string | No | Inicio del período (ISO 8601) |
| `endDate` | string | No | Fin del período (ISO 8601) |
| `mpPreapprovalId` | string | Sí | ID de suscripción recurrente en Mercado Pago |
| `mpPreapprovalStatus` | string | Sí | Estado en Mercado Pago (`authorized`, `paused`, `cancelled`) |
| `cancelAtPeriodEnd` | boolean | No | Si expira automáticamente al fin del período actual |
| `createdAt` | string | No | ISO 8601 |
| `updatedAt` | string | No | ISO 8601 |

### 📤 DTO: SubscriptionCheckoutResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `subscriptionId` | UUID (string) | No | ID de la suscripción |
| `planId` | UUID (string) | No | ID del plan SaaS |
| `mpPreapprovalId` | string | No | ID del preapproval en Mercado Pago |
| `initPoint` | string | No | URL de Mercado Pago para autorizar el débito |
| `status` | `SubscriptionStatus` | No | Estado actual (`PENDING`, etc.) |

### 📤 DTO: SubscriptionPaymentResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID del registro de cobro |
| `subscriptionId` | UUID (string) | No | ID de la suscripción |
| `mpAuthorizedPaymentId` | string | Sí | ID del pago autorizado en Mercado Pago |
| `mpPaymentId` | string | Sí | ID del pago procesado |
| `status` | `SubscriptionPaymentStatus` | No | `PENDING`, `APPROVED`, `REJECTED`, `REFUNDED` |
| `amount` | BigDecimal | No | Monto cobrado |
| `currency` | string | No | Moneda (`PEN`) |
| `paidAt` | string | Sí | Fecha de pago (ISO 8601) |
| `periodEnd` | string | Sí | Fin de cobertura (ISO 8601) |
| `createdAt` | string | No | ISO 8601 |

---

## DTOs — 3. Tenant / Store

### 📥 DTO: TenantCreateRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `subscriptionId` | UUID | No | Propiedad del usuario | ID de suscripción (auto-resuelto si se omite) |
| `name` | string | Sí | No vacío, máx. 150 | Nombre comercial |
| `slug` | string | Sí | `^[a-z0-9-]+$`, máx. 100 | Slug único del storefront |
| `logoUrl` | string | No | Máx. 500 | URL del logotipo |
| `bannerUrl` | string | No | Máx. 500 | URL del banner |
| `phone` | string | No | Máx. 20 | Teléfono |
| `address` | string | No | Máx. 255 | Dirección física |
| `latitude` | Double | No | `-90.0` a `90.0` | Latitud del local |
| `longitude` | Double | No | `-180.0` a `180.0` | Longitud del local |
| `deliveryRadiusKm` | BigDecimal | No | `0.00` a `100.00` (default `5.00`) | Radio de reparto (km) |
| `defaultDeliveryFee` | BigDecimal | No | `0.00` a `1000.00` (default `0.00`) | Costo estándar de delivery |
| `openingTime` | string (`LocalTime`) | No | Formato `HH:mm:ss` o `HH:mm` | Hora de apertura |
| `closingTime` | string (`LocalTime`) | No | Formato `HH:mm:ss` o `HH:mm` | Hora de cierre |

### 📥 DTO: TenantUpdateRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `name` | string | Sí | No vacío, máx. 150 | Nombre comercial |
| `logoUrl` | string | No | Máx. 500 | URL del logotipo |
| `bannerUrl` | string | No | Máx. 500 | URL del banner |
| `phone` | string | No | Máx. 20 | Teléfono |
| `address` | string | No | Máx. 255 | Dirección |
| `latitude` | Double | No | `-90.0` a `90.0` | Latitud (enviar junto con `longitude`) |
| `longitude` | Double | No | `-180.0` a `180.0` | Longitud (enviar junto con `latitude`) |
| `deliveryRadiusKm` | BigDecimal | No | `0.00` a `100.00` | Radio de reparto |
| `defaultDeliveryFee` | BigDecimal | No | `0.00` a `1000.00` | Tarifa estándar de delivery |
| `isActive` | boolean | No | — | Estado operativo |
| `openingTime` | string (`LocalTime`) | No | Formato `HH:mm:ss` o `HH:mm` | Hora de apertura |
| `closingTime` | string (`LocalTime`) | No | Formato `HH:mm:ss` o `HH:mm` | Hora de cierre |
| `isOpen` | boolean | No | — | Estado abierto/cerrado |

### 📥 DTO: TenantStatusRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `isActive` | boolean | Sí | `@NotNull` | Estado del restaurante |

### 📥 DTO: TenantMemberRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `email` | string | Sí | `@NotBlank("El correo electrónico del usuario es obligatorio")`, `@Email("Debe proporcionar un correo electrónico válido")` | Correo electrónico del usuario registrado a invitar |
| `role` | `TenantStaffRole` | Sí | `@NotNull` | Rol asignado |

### 📥 DTO: MemberRoleUpdateRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `role` | `TenantStaffRole` | Sí | `@NotNull` | Nuevo rol operativo |

### 📥 DTO: CustomerBlockRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `isBlocked` | boolean | Sí | `@NotNull` | Estado de bloqueo |

### 📥 DTO: CustomerNotesRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `notes` | string | No | Máx. 2000 | Notas internas del staff |

### 📥 DTO: CustomerPointsAdjustmentRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `adjustment` | integer | Sí | `@NotNull`, `-100000` a `100000` | Delta de puntos de lealtad |

### 📥 DTO: StoreRegisterRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `fullName` | string | Sí | No vacío, máx. 150 | Nombre completo |
| `email` | string | Sí | Email válido, máx. 255 | Correo electrónico |
| `password` | string | Sí | 8–100 chars, política de contraseñas | Contraseña |
| `phone` | string | No | Máx. 20 | Teléfono |

### 📥 DTO: StoreLoginRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `email` | string | Sí | Email válido, máx. 255 | Correo electrónico |
| `password` | string | Sí | No vacío, máx. 100 | Contraseña |

### 📤 DTO: TenantResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID del restaurante |
| `subscriptionId` | UUID (string) | Sí | ID de suscripción vinculada |
| `ownerId` | UUID (string) | No | ID del usuario propietario |
| `name` | string | No | Nombre comercial |
| `slug` | string | No | Slug único |
| `logoUrl` | string | Sí | URL del logo |
| `bannerUrl` | string | Sí | URL del banner |
| `phone` | string | Sí | Teléfono |
| `address` | string | Sí | Dirección física |
| `latitude` | Double | Sí | Latitud WGS84 |
| `longitude` | Double | Sí | Longitud WGS84 |
| `deliveryRadiusKm` | BigDecimal | No | Radio de entrega en km |
| `defaultDeliveryFee` | BigDecimal | No | Tarifa estándar de delivery |
| `isMpConnected` | boolean | No | Si tiene Mercado Pago conectado |
| `onlinePaymentsEnabled` | boolean | No | Si acepta pagos online en storefront |
| `mpUserId` | string | Sí | ID de cuenta Mercado Pago |
| `mpConnectedAt` | string | Sí | Fecha de conexión MP (ISO 8601) |
| `mpTokenExpiresAt` | string | Sí | Expiración token OAuth MP (ISO 8601) |
| `isActive` | boolean | No | Estado activo |
| `openingTime` | string (`LocalTime`) | Sí | Hora de apertura (`HH:mm:ss`) |
| `closingTime` | string (`LocalTime`) | Sí | Hora de cierre (`HH:mm:ss`) |
| `isOpen` | boolean | No | Estado efectivo abierto/cerrado (evalúa `openingTime`/`closingTime` cuando están configurados) |
| `createdAt` | string | No | ISO 8601 |
| `updatedAt` | string | No | ISO 8601 |

### 📤 DTO: TenantMemberResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID de la membresía (`TenantMember`) o de la invitación (`TenantInvitation`) |
| `tenantId` | UUID (string) | No | ID del restaurante |
| `userId` | UUID (string) | No | ID del usuario |
| `userFullName` | string | No | Nombre completo del empleado/invitado |
| `userEmail` | string | No | Correo electrónico del empleado/invitado |
| `role` | `TenantStaffRole` | No | Rol asignado/ofrecido |
| `isActive` | boolean | No | Estado activo (`true` para miembros activos o invitaciones aceptadas; `false` en invitaciones `PENDING`/`REJECTED`) |
| `createdAt` | string | No | ISO 8601 |
| `updatedAt` | string | No | ISO 8601 |
| `invitationStatus` | `TenantInvitationStatus` | Sí | Estado de invitación (`PENDING`, `ACCEPTED`, `REJECTED`), o `null` cuando proviene de `TenantMember` |

### 📤 DTO: TenantCustomerResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID del cliente (igual a `userId`) |
| `tenantId` | UUID (string) | No | ID del restaurante |
| `tenantName` | string | No | Nombre del restaurante |
| `tenantSlug` | string | No | Slug del restaurante |
| `userId` | UUID (string) | No | ID de usuario global |
| `userFullName` | string | No | Nombre del cliente |
| `userEmail` | string | No | Email del cliente |
| `loyaltyPoints` | integer | No | Saldo de puntos de lealtad |
| `isBlocked` | boolean | No | Si está bloqueado en esta tienda |
| `notes` | string | Sí | Notas privadas del restaurante |
| `totalOrders` | integer | No | Total de pedidos entregados |
| `firstOrderAt` | string | Sí | Fecha del primer pedido (ISO 8601) |
| `lastOrderAt` | string | Sí | Fecha del último pedido (ISO 8601) |
| `createdAt` | string | No | ISO 8601 |
| `updatedAt` | string | No | ISO 8601 |

### 📤 DTO: StoreAuthResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `accessToken` | string | No | JWT de acceso |
| `refreshToken` | string | No | JWT de refresco |
| `tokenType` | string | No | `"Bearer"` |
| `expiresIn` | number (long) | No | Segundos hasta expiración |
| `user` | `UserResponse` | No | Datos globales del usuario |
| `customerProfile` | `TenantCustomerResponse` | No | Perfil del cliente en la tienda |

---

## DTOs — 4. Catalog

### 📥 DTO: TaxRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `name` | string | Sí | No vacío, máx. 50 | Nombre del impuesto |
| `rate` | BigDecimal | Sí | `0.00`–`100.00`, `@Digits(3, 2)` | Tasa porcentual |
| `code` | string | No | Máx. 20 | Código fiscal (ej. SUNAT) |
| `isInclusive` | boolean | No | Default: `true` | Incluido en el precio de venta |
| `isActive` | boolean | No | Default: `true` | Estado activo |

### 📥 DTO: TaxStatusRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `isActive` | boolean | Sí | `@NotNull` | Nuevo estado |

### 📥 DTO: CategoryRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `name` | string | Sí | No vacío, máx. 100 | Nombre de la categoría |
| `description` | string | No | Máx. 500 | Descripción |
| `sortOrder` | integer | No | `≥ 0` (default `0`) | Orden en carta |
| `isActive` | boolean | No | Default: `true` | Estado activo |

### 📥 DTO: CategoryStatusRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `isActive` | boolean | Sí | `@NotNull` | Nuevo estado |

### 📥 DTO: CategorySortOrderRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `sortOrder` | integer | Sí | `@NotNull`, `≥ 0` | Nueva posición |

### 📥 DTO: ProductRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `categoryId` | UUID | No | Categoría del tenant | Categoría asignada |
| `taxId` | UUID | No | Impuesto activo del tenant | Impuesto asignado |
| `name` | string | Sí | No vacío, máx. 150 | Nombre del producto |
| `description` | string | No | Máx. 1000 | Descripción |
| `price` | BigDecimal | Condicional | `0.01` a `999999.99` | Precio base (si no viene en `prices`) |
| `imageUrl` | string | No | Máx. 500 | URL de imagen |
| `stock` | integer | No | `≥ 0` (default `0`) | Stock disponible |
| `isAvailable` | boolean | No | Default: `true` | Disponibilidad |
| `prices` | `List<ProductPriceRequest>` | No | `@Valid` (solo `POST`) | Reglas de precios iniciales |

### 📥 DTO: ProductAvailabilityRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `isAvailable` | boolean | Sí | `@NotNull` | Disponibilidad |

### 📥 DTO: ProductStockRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `stock` | integer | Sí | `@NotNull`, `≥ 0` | Stock absoluto |

### 📥 DTO: ProductPriceRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `id` | UUID | No | Ignorado en `POST` | ID de regla |
| `name` | string | Sí | No vacío, máx. 100 | Nombre de la regla |
| `price` | BigDecimal | Sí | `@NotNull`, `≥ 0.00` | Precio aplicable |
| `isBase` | boolean | No | Default: `false` | Indica precio base |
| `discountPercentage` | BigDecimal | No | `0.00` a `100.00` | Descuento porcentual |
| `startDate` | string | No | `YYYY-MM-DD` | Fecha inicio |
| `endDate` | string | No | `YYYY-MM-DD` (`>= startDate`) | Fecha fin |
| `startTime` | string | No | `HH:mm:ss` | Hora inicio |
| `endTime` | string | No | `HH:mm:ss` (`!= startTime`) | Hora fin |
| `daysOfWeek` | Short[] | No | Valores `1` a `7` (ISO Lun–Dom) | Días aplicables |
| `priority` | integer | No | Default: `0` | Prioridad |
| `isActive` | boolean | No | Default: `true` | Estado activo |

### 📤 DTO: TaxResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID del impuesto |
| `tenantId` | UUID (string) | No | Restaurante |
| `name` | string | No | Nombre del impuesto |
| `rate` | BigDecimal | No | Tasa porcentual |
| `code` | string | Sí | Código fiscal |
| `isInclusive` | boolean | No | Si está incluido en precio |
| `isActive` | boolean | No | Estado activo |
| `createdAt` | string | No | ISO 8601 |
| `updatedAt` | string | No | ISO 8601 |

### 📤 DTO: CategoryResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID de la categoría |
| `tenantId` | UUID (string) | No | Restaurante |
| `name` | string | No | Nombre |
| `description` | string | Sí | Descripción |
| `sortOrder` | integer | No | Posición |
| `isActive` | boolean | No | Estado activo |
| `createdAt` | string | No | ISO 8601 |
| `updatedAt` | string | No | ISO 8601 |

### 📤 DTO: ProductPriceResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID de la regla |
| `productId` | UUID (string) | No | Producto |
| `name` | string | No | Nombre de la regla |
| `price` | BigDecimal | No | Precio |
| `isBase` | boolean | No | Si es precio base |
| `discountPercentage` | BigDecimal | Sí | Descuento % |
| `startDate` | string | Sí | `YYYY-MM-DD` |
| `endDate` | string | Sí | `YYYY-MM-DD` |
| `startTime` | string | Sí | `HH:mm:ss` |
| `endTime` | string | Sí | `HH:mm:ss` |
| `daysOfWeek` | Short[] | Sí | Días ISO (`1`–`7`) |
| `priority` | integer | No | Prioridad |
| `isActive` | boolean | No | Estado activo |
| `createdAt` | string | No | ISO 8601 |
| `updatedAt` | string | No | ISO 8601 |

### 📤 DTO: ProductResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID del producto |
| `tenantId` | UUID (string) | No | Restaurante |
| `categoryId` | UUID (string) | Sí | Categoría |
| `categoryName` | string | Sí | Nombre de la categoría |
| `taxId` | UUID (string) | Sí | Impuesto configurado |
| `taxName` | string | Sí | Nombre del impuesto |
| `taxRate` | BigDecimal | Sí | Tasa porcentual |
| `taxInclusive` | boolean | Sí | Si el impuesto es inclusivo |
| `name` | string | No | Nombre |
| `description` | string | Sí | Descripción |
| `price` | BigDecimal | No | Precio efectivo vigente |
| `basePrice` | BigDecimal | No | Precio base |
| `prices` | `List<ProductPriceResponse>` | No | Reglas de precios configuradas |
| `imageUrl` | string | Sí | Imagen |
| `stock` | integer | No | Unidades en stock |
| `isAvailable` | boolean | No | Disponibilidad |
| `createdAt` | string | No | ISO 8601 |
| `updatedAt` | string | No | ISO 8601 |

### 📤 DTO: StorefrontMenuResponse / StorefrontCategoryResponse / StorefrontProductResponse

- **`StorefrontMenuResponse`**: `tenantId` (UUID), `tenantName` (string), `tenantSlug` (string), `logoUrl` (string, nullable), `bannerUrl` (string, nullable), `onlinePaymentsEnabled` (boolean), `openingTime` (`LocalTime`, nullable), `closingTime` (`LocalTime`, nullable), `isOpen` (boolean), `categories` (`List<StorefrontCategoryResponse>`), `uncategorizedProducts` (`List<StorefrontProductResponse>`).
- **`StorefrontCategoryResponse`**: `id` (UUID), `name` (string), `description` (string, nullable), `sortOrder` (integer), `products` (`List<StorefrontProductResponse>`).
- **`StorefrontProductResponse`**:

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID del producto |
| `tenantId` | UUID (string) | No | Restaurante |
| `categoryId` | UUID (string) | Sí | Categoría |
| `categoryName` | string | Sí | Nombre de categoría |
| `name` | string | No | Nombre del producto |
| `description` | string | Sí | Descripción |
| `imageUrl` | string | Sí | Imagen |
| `price` | BigDecimal | No | Precio efectivo vigente |
| `basePrice` | BigDecimal | No | Precio base regular |
| `activePromotionName` | string | Sí | Nombre de promoción vigente |
| `discountPercentage` | BigDecimal | Sí | Descuento % de promoción vigente |
| `taxId` | UUID (string) | Sí | ID del impuesto activo |
| `taxName` | string | Sí | Nombre del impuesto |
| `taxRate` | BigDecimal | No | Tasa aplicada (`0.00` si exento) |
| `taxInclusive` | boolean | Sí | Si el impuesto es inclusivo |
| `baseUnitPrice` | BigDecimal | No | Valor unitario neto sin impuesto |
| `taxAmount` | BigDecimal | No | Impuesto unitario |
| `finalPrice` | BigDecimal | No | Precio final al comensal |
| `stock` | integer | No | Stock disponible (`0` = agotado) |
| `isAvailable` | boolean | No | Disponibilidad |

---

## DTOs — 5. Cart

### 📥 DTO: CartItemRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `productId` | UUID | Sí | `@NotNull` | Producto de la tienda |
| `quantity` | integer | Sí | `@NotNull`, `1` a `999` | Cantidad |
| `notes` | string | No | Máx. 500 | Notas de preparación |

### 📥 DTO: CartItemUpdateRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `quantity` | integer | No* | `1` a `999` | Nueva cantidad |
| `notes` | string | No* | Máx. 500 | Nuevas notas |

### 📥 DTO: CartNotesRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `notes` | string | No | Máx. 1000 | Notas generales del carrito |

### 📤 DTO: CartResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID del carrito |
| `tenantId` | UUID (string) | No | Restaurante |
| `tenantSlug` | string | Sí | Slug |
| `tenantName` | string | Sí | Nombre del restaurante |
| `customerId` | UUID (string) | No | ID de cliente |
| `customerUserId` | UUID (string) | No | Usuario global del cliente |
| `total` | BigDecimal | No | Total acumulado de los ítems |
| `notes` | string | Sí | Notas generales |
| `items` | `List<CartItemResponse>` | No | Ítems del carrito |
| `hasPriceChanges` | boolean | No | Si algún ítem cambió de precio |
| `hasUnavailableItems` | boolean | No | Si algún ítem está agotado, sin stock suficiente o inactivo |
| `warnings` | `List<string>` | No | Advertencias legibles para mostrar al cliente |
| `createdAt` | string | No | ISO 8601 |
| `updatedAt` | string | No | ISO 8601 |

### 📤 DTO: CartItemResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID del ítem |
| `productId` | UUID (string) | No | Producto |
| `productName` | string | No | Nombre del producto |
| `productImageUrl` | string | Sí | Imagen |
| `unitPrice` | BigDecimal | No | Precio unitario vigente |
| `previousUnitPrice` | BigDecimal | Sí | Precio anterior si hubo cambio |
| `priceChanged` | boolean | No | Si cambió de precio |
| `productAvailable` | boolean | No | Disponible, categoría activa y stock suficiente |
| `productStock` | integer | No | Stock actual en catálogo |
| `quantity` | integer | No | Cantidad seleccionada |
| `subtotal` | BigDecimal | No | `quantity × unitPrice` |
| `notes` | string | Sí | Notas de preparación |
| `createdAt` | string | Sí | ISO 8601 |
| `updatedAt` | string | Sí | ISO 8601 |

---

## DTOs — 6. Order

### 📥 DTO: OrderCheckoutRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `deliveryType` | `DeliveryType` | Sí | `@NotNull` | `DELIVERY`, `TAKEAWAY`, `DINE_IN` |
| `customerAddressId` | UUID | No | Propiedad del usuario | Dirección guardada |
| `deliveryAddress` | string | No | Máx. 255 | Dirección en texto |
| `deliveryLatitude` | Double | No | `-90.0` a `90.0` | Latitud WGS84 |
| `deliveryLongitude` | Double | No | `-180.0` a `180.0` | Longitud WGS84 |
| `ruc` | string | No | Máx. 20 | RUC para facturación |
| `notes` | string | No | Máx. 1000 | Notas del pedido |

### 📥 DTO: OrderCancelRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `reason` | string | No | Máx. 500 | Motivo de cancelación |

### 📥 DTO: OrderStatusUpdateRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `status` | `OrderStatus` | Sí | `@NotNull` | Nuevo estado de la orden |

### 📥 DTO: OrderAssignStaffRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `deliveryStaffId` | UUID | Sí | `@NotNull` | `userId` del repartidor activo |

### 📤 DTO: OrderResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID de la orden |
| `tenantId` | UUID (string) | No | Restaurante |
| `tenantSlug` | string | No | Slug de la tienda |
| `tenantName` | string | No | Nombre del restaurante |
| `customerId` | UUID (string) | No | ID de cliente |
| `customerUserId` | UUID (string) | No | ID de usuario global |
| `customerFullName` | string | Sí | Nombre del cliente |
| `customerEmail` | string | Sí | Email del cliente |
| `customerPhone` | string | Sí | Teléfono |
| `deliveryStaffId` | UUID (string) | Sí | ID del repartidor asignado |
| `deliveryStaffName` | string | Sí | Nombre del repartidor |
| `orderNumber` | string | No | Correlativo (`ORD-000142`) |
| `ruc` | string | Sí | RUC |
| `deliveryType` | `DeliveryType` | No | Modalidad de entrega |
| `status` | `OrderStatus` | No | Estado actual |
| `deliveryAddress` | string | Sí | Dirección histórica inmutable |
| `deliveryLatitude` | Double | Sí | Latitud de entrega |
| `deliveryLongitude` | Double | Sí | Longitud de entrega |
| `estimatedDistanceKm` | BigDecimal | Sí | Distancia estimada (km) |
| `estimatedDeliveryMinutes` | integer | Sí | ETA en minutos |
| `estimatedArrivalAt` | string | Sí | Hora estimada de llegada (ISO 8601) |
| `etaMessage` | string | Sí | Mensaje de ETA para UI |
| `dispatchedAt` | string | Sí | Timestamp de despacho (ISO 8601) |
| `deliveredAt` | string | Sí | Timestamp de entrega (ISO 8601) |
| `subtotal` | BigDecimal | No | Subtotal neto imponible |
| `taxTotal` | BigDecimal | No | Impuesto acumulado |
| `deliveryFee` | BigDecimal | No | Costo de envío |
| `total` | BigDecimal | No | Monto total final |
| `notes` | string | Sí | Notas |
| `items` | `List<OrderItemResponse>` | No | Snapshot fiscal de ítems |
| `createdAt` | string | No | ISO 8601 |
| `updatedAt` | string | No | ISO 8601 |

### 📤 DTO: OrderItemResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID del ítem |
| `productId` | UUID (string) | Sí | ID del producto original |
| `productName` | string | No | Snapshot inmutable del nombre |
| `taxId` | UUID (string) | Sí | Impuesto aplicado |
| `taxRate` | BigDecimal | No | Snapshot de tasa (%) |
| `taxAmount` | BigDecimal | No | Snapshot de impuesto calculado |
| `unitPrice` | BigDecimal | No | Snapshot de precio unitario base |
| `quantity` | integer | No | Cantidad |
| `subtotal` | BigDecimal | No | Subtotal neto del ítem |
| `notes` | string | Sí | Notas del ítem |
| `createdAt` | string | No | ISO 8601 |
| `updatedAt` | string | No | ISO 8601 |

---

## DTOs — 7. Payment

### 📤 DTO: CheckoutPaymentResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `paymentId` | UUID (string) | No | ID del intento de pago en NexoFood |
| `mpOrderId` | string | No | ID de la orden en Mercado Pago |
| `checkoutUrl` | string | No | URL de Checkout Pro para redirigir al cliente |
| `status` | `PaymentStatus` | No | Estado inicial (`PENDING`) |
| `amount` | BigDecimal | No | Importe total |
| `currency` | string | No | Moneda (`PEN`) |
| `expiresAt` | string | Sí | Límite para completar el pago (ISO 8601, +30 min) |

### 📤 DTO: MercadoPagoConnectResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `authorizationUrl` | string | No | Enlace oficial OAuth de Mercado Pago |
| `expiresInSeconds` | number (long) | No | Validez del `state` firmado (`600`) |

### 📤 DTO: MercadoPagoStatusResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `tenantId` | UUID (string) | No | Restaurante |
| `connected` | boolean | No | Si tiene credenciales OAuth activas |
| `mpUserId` | string | Sí | ID de cuenta en Mercado Pago |
| `connectedAt` | string | Sí | Fecha de vinculación (ISO 8601) |
| `tokenExpiresAt` | string | Sí | Expiración del token OAuth (ISO 8601) |

### 📤 DTO: PaymentStatusResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `paymentId` | UUID (string) | No | ID del pago |
| `status` | `PaymentStatus` | No | Estado actual del pago |
| `statusDetail` | string | Sí | Detalle devuelto por Mercado Pago |
| `orderId` | UUID (string) | Sí | ID de la orden creada tras `APPROVED` |
| `orderNumber` | string | Sí | Correlativo de la orden (`ORD-XXXXXX`) |
| `amount` | BigDecimal | No | Monto |
| `currency` | string | No | Moneda (`PEN`) |
| `approvedAt` | string | Sí | Fecha de aprobación (ISO 8601) |
| `refundedAt` | string | Sí | Fecha de reembolso (ISO 8601) |

### 📤 DTO: PaymentResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID del pago |
| `orderId` | UUID (string) | Sí | ID de la orden vinculada |
| `orderNumber` | string | Sí | Correlativo de la orden |
| `tenantId` | UUID (string) | No | Restaurante |
| `customerId` | UUID (string) | Sí | Cliente comprador |
| `mpOrderId` | string | Sí | ID de orden en Mercado Pago |
| `mpPaymentId` | string | Sí | ID de transacción en Mercado Pago |
| `paymentMethod` | string | Sí | Medio de pago (`visa`, `yape`, etc.) |
| `status` | `PaymentStatus` | No | Estado del pago |
| `statusDetail` | string | Sí | Detalle devuelto por pasarela |
| `amount` | BigDecimal | No | Importe |
| `currency` | string | No | Moneda (`PEN`) |
| `expiresAt` | string | Sí | Expiración del checkout (ISO 8601) |
| `approvedAt` | string | Sí | Fecha de aprobación (ISO 8601) |
| `refundedAt` | string | Sí | Fecha de reembolso (ISO 8601) |
| `refundReason` | string | Sí | Motivo de reembolso |
| `createdAt` | string | No | ISO 8601 |
| `updatedAt` | string | No | ISO 8601 |

---

## DTOs — 8. Tracking

### 📥 DTO: DriverLocationUpdateRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `latitude` | Double | Sí | `@NotNull`, `-90.0` a `90.0` | Latitud GPS |
| `longitude` | Double | Sí | `@NotNull`, `-180.0` a `180.0` | Longitud GPS |
| `heading` | Double | No | `0.0` a `360.0` | Rumbo en grados |
| `speedKmh` | Double | No | `0.0` a `200.0` | Velocidad (km/h) |
| `accuracyM` | Double | No | `≥ 0.0` | Precisión GPS (metros) |

### 📤 DTO: DeliveryEstimateResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `tenantId` | UUID (string) | No | Restaurante |
| `tenantSlug` | string | No | Slug |
| `tenantName` | string | No | Nombre del restaurante |
| `withinCoverage` | boolean | No | Si la ubicación está dentro de `deliveryRadiusKm` |
| `maxDeliveryRadiusKm` | BigDecimal | Sí | Radio máximo configurado |
| `straightDistanceKm` | BigDecimal | No | Distancia lineal (km) |
| `estimatedRouteDistanceKm` | BigDecimal | No | Distancia vial estimada (`× 1.35`) |
| `deliveryFee` | BigDecimal | No | Tarifa de envío |
| `estimatedDeliveryMinutes` | integer | Sí | Minutos estimados (`null` si fuera de cobertura) |
| `minMinutes` | integer | Sí | Límite inferior (`null` si fuera de cobertura) |
| `maxMinutes` | integer | Sí | Límite superior (`null` si fuera de cobertura) |
| `estimatedArrivalAt` | string | Sí | Hora estimada de llegada (ISO 8601) |
| `message` | string | No | Mensaje descriptivo para el comensal |

### 📤 DTO: DeliveryTrackingResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `orderId` | UUID (string) | No | ID de la orden |
| `orderNumber` | string | No | Correlativo (`ORD-XXXXXX`) |
| `orderStatus` | `OrderStatus` | No | Estado de la orden |
| `tenantId` | UUID (string) | No | Restaurante |
| `tenantName` | string | No | Nombre del restaurante |
| `storeLatitude` | Double | Sí | Latitud del local |
| `storeLongitude` | Double | Sí | Longitud del local |
| `deliveryAddress` | string | Sí | Dirección de destino |
| `destinationLatitude` | Double | Sí | Latitud de destino |
| `destinationLongitude` | Double | Sí | Longitud de destino |
| `driverId` | UUID (string) | Sí | ID del repartidor |
| `driverName` | string | Sí | Nombre del repartidor |
| `driverPhone` | string | Sí | Teléfono del repartidor |
| `driverLatitude` | Double | Sí | Última latitud del repartidor |
| `driverLongitude` | Double | Sí | Última longitud del repartidor |
| `heading` | Double | Sí | Rumbo (0°–360°) |
| `speedKmh` | Double | Sí | Velocidad (km/h) |
| `accuracyM` | Double | Sí | Precisión GPS (m) |
| `remainingDistanceKm` | BigDecimal | Sí | Distancia restante (km) |
| `etaMinutes` | integer | Sí | ETA restante en minutos |
| `etaMinMinutes` | integer | Sí | Rango mínimo |
| `etaMaxMinutes` | integer | Sí | Rango máximo |
| `estimatedArrivalAt` | string | Sí | Hora estimada de llegada (ISO 8601) |
| `etaMessage` | string | Sí | Mensaje de ETA |
| `trackingActive` | boolean | No | Si el seguimiento está activo |
| `stale` | boolean | No | `true` si el último ping supera `60` segundos en `EN_CAMINO` |
| `throttled` | boolean | No | `true` si el ping llegó antes de `5` segundos |
| `pollIntervalSeconds` | integer | No | Intervalo recomendado de polling (`15` s) |
| `lastUpdatedAt` | string | Sí | Último ping GPS (ISO 8601) |
| `dispatchedAt` | string | Sí | Inicio de ruta (ISO 8601) |
| `deliveredAt` | string | Sí | Confirmación de entrega (ISO 8601) |
| `routeHistory` | `List<TrackingPointResponse>` | No | Historial de puntos GPS (`[]` para clientes) |

### 📤 DTO: TrackingPointResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `latitude` | Double | No | Latitud registrada |
| `longitude` | Double | No | Longitud registrada |
| `heading` | Double | Sí | Rumbo en grados |
| `speedKmh` | Double | Sí | Velocidad (km/h) |
| `accuracyM` | Double | Sí | Precisión GPS (m) |
| `recordedAt` | string | No | Timestamp del punto (ISO 8601) |

---

## DTOs — 9. Notification

### 📥 DTO: NotificationInvitationDecisionRequest

| Campo | Tipo | Requerido | Validación | Descripción |
| --- | --- | --- | --- | --- |
| `decision` | `InvitationActionType` | Sí | `@NotNull("La decisión (ACCEPT o REJECT) es obligatoria")`, valores: `"ACCEPT"` o `"REJECT"` | Acción sobre la invitación |

### 📤 DTO: NotificationResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `id` | UUID (string) | No | ID de la notificación |
| `recipientUserId` | UUID (string) | No | ID del usuario destinatario |
| `tenantId` | UUID (string) | Sí | ID del restaurante relacionado |
| `tenantName` | string | Sí | Nombre del restaurante relacionado |
| `type` | `NotificationType` | No | Tipo de notificación (`STAFF_INVITATION`, `LOW_STOCK`, etc.) |
| `title` | string | No | Título corto (máx. 200 caracteres) |
| `message` | string | No | Cuerpo descriptivo de la notificación |
| `actionRequired` | boolean | No | Si requiere acción explícita del usuario (`true` solo para `STAFF_INVITATION`) |
| `actionStatus` | `NotificationActionStatus` | No | `NONE`, `PENDING`, `ACCEPTED` o `REJECTED` |
| `allowedActions` | `List<string>` | No | `["ACCEPT", "REJECT"]` cuando `actionRequired && actionStatus == "PENDING"`; `[]` en caso contrario |
| `referenceId` | UUID (string) | Sí | ID de la entidad referenciada (ej. `invitationId`, `productId`, `orderId`, `tenantId`, `subscriptionId`) |
| `metadata` | `Map<string, Object>` | Sí | Metadatos contextuales JSONB (varían según el `type`) |
| `isRead` | boolean | No | Si fue leída |
| `readAt` | string | Sí | Fecha/hora de lectura (ISO 8601) |
| `respondedAt` | string | Sí | Fecha/hora de respuesta a invitación (ISO 8601) |
| `createdAt` | string | No | Fecha/hora de creación (ISO 8601) |

### 📤 DTO: UnreadNotificationCountResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `unreadCount` | number (long) | No | Cantidad de notificaciones no leídas del usuario autenticado |

### 📤 DTO: NotificationStreamTicketResponse

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `ticket` | string | No | Token opaco temporal y de un solo uso para autenticar `GET /api/v1/notifications/stream?ticket=...` |
| `expiresInSeconds` | number (long) | No | Tiempo de vida del ticket en segundos (default: `30`) |
| `expiresAt` | string | No | Fecha y hora exacta de expiración del ticket (ISO 8601 UTC) |

### 📤 DTO: NotificationSseConnectedEvent

| Campo | Tipo | Nullable | Descripción |
| --- | --- | --- | --- |
| `status` | string | No | Estado de conexión del stream (`"CONNECTED"`) |
| `unreadCount` | number (long) | No | Cantidad inicial de notificaciones no leídas al abrir el stream |
| `connectedAt` | string | No | Fecha y hora de conexión al stream (ISO 8601 UTC) |

---

# ❌ Códigos de Estado HTTP y Manejo de Errores

| Código | Error | Cuándo se retorna |
| --- | --- | --- |
| `200` | OK | Operación exitosa (lectura, actualización, login, refresh, logout, respuesta a invitación, delete lógico/físico) |
| `201` | Created | Recurso creado exitosamente (registro, plan, suscripción, restaurante, invitación de staff, catálogo, checkout) |
| `302` | Found | Redirección al frontend tras el callback OAuth de Mercado Pago cuando el cliente es un navegador (`Accept: text/html`) |
| `400` | Bad Request | Error de validación de campos (`MethodArgumentNotValidException`), propiedades JSON desconocidas, parámetros/enums inválidos (incl. decisión distinta de `ACCEPT`/`REJECT`), restaurante cerrado en checkout o reglas de negocio infringidas |
| `401` | Unauthorized | Token ausente, inválido o expirado; credenciales incorrectas; cuenta bloqueada; firma de webhook inválida |
| `403` | Forbidden | Sin permisos suficientes, cuenta desactivada, invitación de otro usuario o cliente bloqueado en el comercio |
| `404` | Not Found | Recurso no encontrado o tienda/producto inactivo en storefront |
| `409` | Conflict | Recurso duplicado (email, slug, plan, categoría), invitación pendiente duplicada, invitación ya respondida, relación 1:1 ya existente, solapamiento de reglas de precio o transición de orden/cancelación inválida |
| `429` | Too Many Requests | Límite de tasa excedido en endpoints de autenticación (`10 req / 60s` por IP + grupo; revisar header `Retry-After`) |
| `500` | Internal Server Error | Error interno no controlado en el servidor |
| `502` | Bad Gateway | Fallo de comunicación o respuesta inválida al invocar la pasarela externa de Mercado Pago (`PaymentGatewayException`) |

### Formato de Error de Validación (Jakarta Validation)

Cuando falla la validación de campos (`MethodArgumentNotValidException`), el campo `message` concatena todos los campos inválidos y sus mensajes:

```json
{
  "message": "Error de validación: email: Formato de email inválido, password: La contraseña es obligatoria",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/auth/register",
  "method": "POST"
}
```

### Formato de Error por Campo Desconocido (JSON Estricto)

El backend rechaza propiedades desconocidas en los cuerpos JSON (`fail-on-unknown-properties: true`):

```json
{
  "message": "El campo 'campoInexistente' no es válido o no está permitido en esta solicitud",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/tenants",
  "method": "POST"
}
```

### Formato de Error por Rate Limiting (`429 Too Many Requests`)

```json
{
  "message": "Demasiadas solicitudes de autenticación. Por favor, intente nuevamente más tarde",
  "error": "Too Many Requests",
  "status": 429,
  "timestamp": "2026-11-18T14:00:00",
  "path": "/api/v1/auth/login",
  "method": null
}
```

---

# 📝 Notas Adicionales para el Frontend

1. **Tokens en Headers:** Enviar siempre `Authorization: Bearer <accessToken>` en las peticiones autenticadas; **no** se utilizan cookies ni se envían JWTs en query params. *(Única excepción para `EventSource` nativo: usar el ticket opaco temporal y de un solo uso obtenido en `POST /api/v1/notifications/stream-ticket` en `GET /api/v1/notifications/stream?ticket=...`.)*
2. **UUIDs como Strings:** Todos los identificadores son cadenas UUID de 36 caracteres. No intentar parsearlos como números.
3. **Fechas ISO 8601 y Horarios `LocalTime`:** Todas las fechas y horas viajan en formato ISO 8601 (`OffsetDateTime` con zona horaria en DTOs de dominio, `LocalDateTime` en `ErrorResponse.timestamp`). Los horarios de tienda (`openingTime`, `closingTime`) y reglas de precio (`startTime`, `endTime`) usan `LocalTime` (`HH:mm:ss`). Formatear localmente en el cliente con `Intl.DateTimeFormat` o `date-fns`.
4. **JSON Estricto:** No enviar campos extra ni propiedades no documentadas en los payloads de request para evitar errores `400 Bad Request`.
5. **Coordenadas WGS84 (SRID 4326):** Enviar siempre `latitude` (`-90.0` a `90.0`) y `longitude` (`-180.0` a `180.0`) como números (`Double`).
6. **CORS:** En desarrollo local están permitidos `http://localhost:3000` y `http://localhost:5173` con `allowCredentials = true`, exponiendo el header `Authorization`. En producción se controla mediante la variable de entorno `CORS_ALLOWED_ORIGINS`.
7. **Swagger UI:** Disponible en `http://localhost:8080/swagger-ui/index.html` únicamente en el perfil `dev` para explorar y probar endpoints interactivamente.
8. **Paginación:** Todos los endpoints paginados retornan `PageResponse<T>` (`page`, `size`, `totalElements`, `totalPages`, `first`, `last`, `content`), excepto `GET /api/v1/admin/tenants/{tenantId}/customers`, que retorna directamente `Page<TenantCustomerResponse>` de Spring Data (`number` en lugar de `page`).
9. **Notificaciones Accionables:** Para `STAFF_INVITATION`, renderizar únicamente las acciones devueltas en `allowedActions` (`["ACCEPT", "REJECT"]`) mientras `actionRequired === true && actionStatus === "PENDING"`.
10. **Notificaciones en Tiempo Real (SSE):** Suscribirse a `GET /api/v1/notifications/stream` (vía `?ticket=` temporal de `POST /api/v1/notifications/stream-ticket` o header `Authorization: Bearer`) para recibir eventos `connected`, `notification` y `unread-count` al instante sin recargar la página.
