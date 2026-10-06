# 📖 NexoFood API — Documentación para Frontend

> **Proyecto:** NexoFood API — Plataforma SaaS Multi-Tenant para Restaurantes  
> **Stack:** Java 21 · Spring Boot 4.1.1 · PostgreSQL 16 + PostGIS · Hibernate Spatial  
> **Versión:** 1.0  
> **Última actualización:** 2026-10-06  
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

| Claim    | Tipo   | Descripción                                            |
|----------|--------|--------------------------------------------------------|
| `sub`    | string | Email del usuario                                      |
| `role`   | string | Rol global del sistema (`SUPERADMIN` o `USER`)         |
| `userId` | string | UUID del usuario                                       |
| `iat`    | number | Timestamp UNIX de emisión                              |
| `exp`    | number | Timestamp UNIX de expiración                           |

### Roles de Staff en el Restaurante (`TenantStaffRole`):
- `OWNER`: Dueño y administrador total del restaurante.
- `ADMIN`: Administrador delegado con permisos de catálogo, miembros y pedidos.
- `COCINERO`: Visualización de pedidos en preparación y actualización de estado.
- `REPARTIDOR`: Gestión de pedidos en camino y confirmación de entrega.

### Configuración del Cliente Axios (Recomendada)

```javascript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
  headers: {
    'Content-Type': 'application/json'
  }
});

// Interceptor de petición: inyecta el token Bearer si existe
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Interceptor de respuesta: auto-refresh de token
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

        // Guardar nuevos tokens rotados
        localStorage.setItem('accessToken', data.data.accessToken);
        localStorage.setItem('refreshToken', data.data.refreshToken);

        // Reintentar la petición original
        originalRequest.headers.Authorization = `Bearer ${data.data.accessToken}`;
        return api.request(originalRequest);
      } catch (refreshError) {
        // Fallo de refresh: sesión caducada
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

### ✅ Envelope de Éxito: `ApiResponse<T>`

Todas las respuestas exitosas de la API están envueltas en este formato uniforme:

```json
{
  "success": true,
  "message": "Operación realizada con éxito",
  "data": { ... }
}
```

| Campo     | Tipo    | Descripción                                           |
|-----------|---------|-------------------------------------------------------|
| `success` | boolean | Siempre `true` en peticiones exitosas                 |
| `message` | string  | Mensaje descriptivo para UI o logs                    |
| `data`    | T       | Payload de la respuesta (objeto, array o paginación)  |

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

| Campo       | Tipo   | Descripción                                          |
|-------------|--------|------------------------------------------------------|
| `message`   | string | Mensaje legible del error (incluye fallos de validación)|
| `error`     | string | Código o nombre del error HTTP                       |
| `status`    | number | Código de estado HTTP                                |
| `timestamp` | string | Fecha y hora de generación (ISO 8601 local)          |
| `path`      | string | Ruta del endpoint invocado                           |
| `method`    | string | Método HTTP utilizado                                |

### Códigos de Estado HTTP

| Código | Error                 | Descripción                                                    |
|--------|-----------------------|----------------------------------------------------------------|
| `200`  | OK                    | Consulta, actualización o autenticación completada exitosamente|
| `201`  | Created               | Recurso creado exitosamente                                    |
| `400`  | Bad Request           | Error de validación de campos o reglas de negocio infringidas   |
| `401`  | Unauthorized          | Token ausente, expirado, inválido o credenciales erróneas      |
| `403`  | Forbidden             | Sin permisos suficientes para la acción o cuenta desactivada    |
| `404`  | Not Found             | El recurso solicitado no existe                                |
| `409`  | Conflict              | Conflicto de unicidad (email, slug o relación 1:1 duplicada)   |
| `500`  | Internal Server Error | Error no controlado en el servidor                             |

---

## 🟢 Estado Actual de los Módulos

| Bounded Context   | Estado                       | Controladores Activos                                       |
|-------------------|------------------------------|-------------------------------------------------------------|
| **1. Identity**   | ✅ Activo y testeado         | `AuthController` (`/api/v1/auth/**`)                        |
| **2. Store / Tenant** | ✅ Activo y testeado     | `TenantController`, `TenantMemberController`, `TenantCustomerController`, `StoreAuthController` |
| **3. Catalog & Tax**  | 🔧 Servicios y DTOs listos | Lógica tributaria, precios y catálogo listos para controllers |
| **4. Subscription**   | 🔧 Modelos y DTOs listos   | DTOs y persistencia JPA completos                          |
| **5. Cart**           | 🔧 Modelos y DTOs listos   | DTOs y reglas de negocio multi-tenant completas            |
| **6. Order**          | 🔧 Servicios y DTOs listos | Cálculo de impuestos, snapshots y cobertura listos          |
| **7. Payment**        | 🔧 Modelos y DTOs listos   | Integración y entidades listas                             |
| **8. Inventory**      | 🔧 Modelos y DTOs listos   | Gestión de stock y movimientos listos                      |

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
|------------|--------|-----------|------------------------------|--------------------------|
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
|------------|--------|-----------|--------------|------------------------|
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

| Campo          | Tipo   | Requerido | Validación   | Descripción                            |
|----------------|--------|-----------|--------------|----------------------------------------|
| `refreshToken` | string | Sí        | No en blanco | Refresh token vigente recibido antes   |

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

## 2. Store / Tenant — Gestión de Restaurantes, Staff y Clientes

Módulo central para la administración de restaurantes, asignación de colaboradores y gestión de clientes de tienda.

---

### 🏪 Gestión del Restaurante (`TenantController`)

---

#### POST `/api/v1/tenants`

> Registra un nuevo restaurante vinculado a una suscripción activa y a su dueño.

**Autenticación:** Requerida  
**Rol requerido:** `USER` (que tenga suscripción activa)  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON):**

| Campo                | Tipo       | Requerido | Validación                                  | Descripción                     |
|----------------------|------------|-----------|---------------------------------------------|---------------------------------|
| `subscriptionId`     | UUID       | Sí        | UUID válido                                 | ID de la suscripción SaaS       |
| `ownerId`            | UUID       | Sí        | UUID válido                                 | ID del usuario propietario      |
| `name`               | string     | Sí        | Máx. 150 caracteres                         | Nombre comercial                |
| `slug`               | string     | Sí        | Regex `^[a-z0-9-]+$`, máx. 100 caracteres   | Slug para la URL del storefront |
| `logoUrl`            | string     | No        | URL válida                                  | Logo de la tienda               |
| `bannerUrl`          | string     | No        | URL válida                                  | Banner publicitario             |
| `phone`              | string     | No        | Máx. 20 caracteres                          | Teléfono del restaurante        |
| `address`            | string     | No        | Texto descriptivo                           | Dirección física del local      |
| `latitude`           | number     | No        | WGS84 (-90 a 90)                            | Latitud del local               |
| `longitude`          | number     | No        | WGS84 (-180 a 180)                          | Longitud del local              |
| `deliveryRadiusKm`   | BigDecimal | No        | ≥ 0.00 (default: 5.00)                      | Radio de reparto en kilómetros  |
| `defaultDeliveryFee` | BigDecimal | No        | ≥ 0.00 (default: 0.00)                      | Tarifa de envío estándar        |

**Ejemplo:**
```json
{
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
  "defaultDeliveryFee": 5.00
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
    "deliveryRadiusKm": 6.50,
    "defaultDeliveryFee": 5.00,
    "isMpConnected": false,
    "mpUserId": null,
    "mpConnectedAt": null,
    "isActive": true,
    "createdAt": "2026-10-06T14:00:00Z",
    "updatedAt": "2026-10-06T14:00:00Z"
  }
}
```

---

#### GET `/api/v1/tenants/{id}`

> Obtiene los datos detallados de un restaurante por su identificador único UUID.

**Autenticación:** Requerida  
**Rol requerido:** Cualquier usuario autenticado  

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
    "deliveryRadiusKm": 6.50,
    "defaultDeliveryFee": 5.00,
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
    "deliveryRadiusKm": 6.50,
    "defaultDeliveryFee": 5.00,
    "isMpConnected": true,
    "isActive": true
  }
}
```

---

#### PUT `/api/v1/tenants/{id}`

> Actualiza la información operativa y comercial del restaurante.

**Autenticación:** Requerida  
**Rol requerido:** `OWNER` o `ADMIN` del restaurante  
**Content-Type:** `application/json`

##### 📥 Request

**Body (JSON):**

| Campo                | Tipo       | Requerido | Validación             | Descripción                    |
|----------------------|------------|-----------|------------------------|--------------------------------|
| `name`               | string     | Sí        | Máx. 150 caracteres    | Nombre comercial               |
| `logoUrl`            | string     | No        | —                      | Logo                           |
| `bannerUrl`          | string     | No        | —                      | Banner                         |
| `phone`              | string     | No        | Máx. 20 caracteres     | Teléfono                       |
| `address`            | string     | No        | —                      | Dirección                      |
| `latitude`           | number     | No        | Double                 | Latitud                        |
| `longitude`          | number     | No        | Double                 | Longitud                       |
| `deliveryRadiusKm`   | BigDecimal | No        | ≥ 0.00                 | Radio de cobertura             |
| `defaultDeliveryFee` | BigDecimal | No        | ≥ 0.00                 | Costo de envío base            |
| `isActive`           | boolean    | No        | —                      | Estado                         |

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
    "deliveryRadiusKm": 7.00,
    "defaultDeliveryFee": 6.00,
    "isActive": true
  }
}
```

---

#### PATCH `/api/v1/tenants/{id}/status`

> Activa o desactiva las operaciones del restaurante.

**Autenticación:** Requerida  
**Rol requerido:** `OWNER`  
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

> Obtiene el restaurante asociado al usuario autenticado actual como propietario.

**Autenticación:** Requerida  
**Rol requerido:** Usuario autenticado con rol `OWNER`  

##### 📤 Response

**Éxito (200 OK):**
```json
{
  "success": true,
  "message": "Restaurante del usuario obtenido exitosamente",
  "data": {
    "id": "e1a2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Burger Bros",
    "slug": "burger-bros",
    "isActive": true
  }
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

| Campo    | Tipo   | Requerido | Validación                           | Descripción            |
|----------|--------|-----------|--------------------------------------|------------------------|
| `userId` | UUID   | Sí        | UUID válido                          | ID del usuario global  |
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

> Modifica el rol asignado a un empleado del restaurante.

**Autenticación:** Requerida  
**Rol requerido:** `OWNER`  
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

| Campo      | Tipo   | Requerido | Validación                   | Descripción              |
|------------|--------|-----------|------------------------------|--------------------------|
| `fullName` | string | Sí        | Máx. 150 caracteres          | Nombre completo          |
| `email`    | string | Sí        | Email válido                 | Correo electrónico       |
| `password` | string | Sí        | Mínimo 8 caracteres          | Contraseña               |
| `phone`    | string | No        | Máx. 20 caracteres           | Teléfono de contacto     |

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

## 3. Catalog & Tributos — Categorías, Impuestos y Productos

Este módulo gestiona la oferta de productos, listas de precios, horarios de promoción y el esquema fiscal aplicable.

---

### 🏛️ Estructura Tributaria (`Tax`)

Cada restaurante puede configurar sus propios impuestos (ej. IGV 18%, Impuesto al Consumo, Exonerado 0%).

#### 📥 DTO: TaxRequest

| Campo         | Tipo       | Requerido | Validación                             | Descripción                                  |
|---------------|------------|-----------|----------------------------------------|----------------------------------------------|
| `name`        | string     | Sí        | Máx. 50 caracteres                     | Nombre del tributo (ej. "IGV 18%")           |
| `rate`        | BigDecimal | Sí        | ≥ 0.00, máx. 3 enteros y 2 decimales   | Tasa porcentual (ej. 18.00)                  |
| `code`        | string     | No        | Máx. 20 caracteres                     | Código oficial SUNAT / fiscal (ej. "1000")   |
| `isInclusive` | boolean    | No        | Default: `true`                        | Si el impuesto está incluido en el precio    |
| `isActive`    | boolean    | No        | Default: `true`                        | Estado activo del impuesto                   |

#### 📤 DTO: TaxResponse

| Campo         | Tipo       | Nullable | Descripción                                   |
|---------------|------------|----------|-----------------------------------------------|
| `id`          | UUID       | No       | Identificador único del impuesto              |
| `tenantId`    | UUID       | No       | Restaurante propietario                       |
| `name`        | string     | No       | Nombre del impuesto                           |
| `rate`        | BigDecimal | No       | Tasa del impuesto (ej. 18.00)                 |
| `code`        | string     | Sí       | Código tributario fiscal                      |
| `isInclusive` | boolean    | No       | ¿Precio de lista ya incluye este impuesto?    |
| `isActive`    | boolean    | No       | Estado                                        |
| `createdAt`   | string     | No       | Fecha de creación (ISO 8601)                  |
| `updatedAt`   | string     | No       | Fecha de actualización (ISO 8601)             |

#### 💡 Reglas de Cálculo de Impuestos en NexoFood:
- **Impuesto Inclusivo (`isInclusive = true`, ej. IGV Perú):**
  $$\text{baseUnitPrice} = \frac{\text{unitPrice}}{1 + (\text{rate} / 100)}$$
  $$\text{taxAmount} = \text{unitPrice} - \text{baseUnitPrice}$$
- **Impuesto Exclusivo (`isInclusive = false`):**
  $$\text{baseUnitPrice} = \text{unitPrice}$$
  $$\text{taxAmount} = \text{unitPrice} \times (\text{rate} / 100)$$

---

### 📂 Categorías de Productos

#### 📥 DTO: CategoryRequest

| Campo         | Tipo    | Requerido | Validación          | Descripción                     |
|---------------|---------|-----------|---------------------|---------------------------------|
| `name`        | string  | Sí        | Máx. 100 caracteres | Nombre de la categoría          |
| `description` | string  | No        | —                   | Descripción para la carta       |
| `sortOrder`   | number  | No        | ≥ 0                 | Orden para mostrar en menú      |
| `isActive`    | boolean | No        | Default: `true`     | Si se muestra en el storefront  |

#### 📤 DTO: CategoryResponse

| Campo         | Tipo    | Nullable | Descripción                             |
|---------------|---------|----------|-----------------------------------------|
| `id`          | UUID    | No       | Identificador único                     |
| `tenantId`    | UUID    | No       | Restaurante                             |
| `name`        | string  | No       | Nombre de la categoría                  |
| `description` | string  | Sí       | Descripción                             |
| `sortOrder`   | number  | No       | Posición relativa en la interfaz        |
| `isActive`    | boolean | No       | Estado                                  |
| `createdAt`   | string  | No       | ISO 8601                                |
| `updatedAt`   | string  | No       | ISO 8601                                |

---

### 🍔 Productos y Precios

Un producto cuenta con un precio base y puede tener múltiples precios condicionales (`ProductPriceRequest`) para días especiales, horas felices o canales delivery.

#### 📥 DTO: ProductRequest

| Campo         | Tipo                      | Requerido | Validación                | Descripción                                |
|---------------|---------------------------|-----------|---------------------------|--------------------------------------------|
| `categoryId`  | UUID                      | No        | UUID válido               | Categoría asignada                         |
| `taxId`       | UUID                      | No        | UUID válido               | Impuesto tributario aplicable              |
| `name`        | string                    | Sí        | Máx. 150 caracteres       | Nombre del producto                        |
| `description` | string                    | No        | —                         | Descripción / ingredientes                 |
| `price`       | BigDecimal                | Sí        | ≥ 0.01                    | Precio base de venta al público            |
| `imageUrl`    | string                    | No        | URL válida                | Foto del platillo                          |
| `isAvailable` | boolean                   | No        | Default: `true`           | Disponibilidad en cocina                   |
| `prices`      | List<ProductPriceRequest> | No        | Lista de precios extras   | Reglas de precios dinámicos/promociones    |

#### 📥 DTO: ProductPriceRequest

| Campo                | Tipo       | Requerido | Validación          | Descripción                                     |
|----------------------|------------|-----------|---------------------|-------------------------------------------------|
| `id`                 | UUID       | No        | —                   | ID si es actualización de precio existente      |
| `name`               | string     | Sí        | Máx. 100 caracteres | Nombre (ej. "Happy Hour Jueves", "Fin de Semana")|
| `price`              | BigDecimal | Sí        | ≥ 0.00              | Precio aplicable                                |
| `isBase`             | boolean    | No        | —                   | Si actúa como precio base                       |
| `discountPercentage` | BigDecimal | No        | ≥ 0.00              | Descuento opcional en %                         |
| `startDate`          | string     | No        | Formato `YYYY-MM-DD`| Fecha inicio de vigencia                        |
| `endDate`            | string     | No        | Formato `YYYY-MM-DD`| Fecha fin de vigencia                           |
| `startTime`          | string     | No        | Formato `HH:mm:ss`  | Hora de inicio                                  |
| `endTime`            | string     | No        | Formato `HH:mm:ss`  | Hora de fin                                     |
| `daysOfWeek`         | Short[]    | No        | Array de 1 a 7      | Días aplicables (1=Lunes, 7=Domingo)            |
| `priority`           | number     | No        | Integer             | Prioridad cuando hay coincidencia de horario    |
| `isActive`           | boolean    | No        | —                   | Estado activo                                   |

#### 📤 DTO: ProductResponse

| Campo          | Tipo                       | Nullable | Descripción                                          |
|----------------|----------------------------|----------|------------------------------------------------------|
| `id`           | UUID                       | No       | Identificador único del producto                     |
| `tenantId`     | UUID                       | No       | Restaurante                                          |
| `categoryId`   | UUID                       | Sí       | Categoría                                            |
| `categoryName` | string                     | Sí       | Nombre de la categoría                               |
| `taxId`        | UUID                       | Sí       | ID del impuesto configurado                          |
| `taxName`      | string                     | Sí       | Nombre del impuesto (ej. "IGV 18%")                  |
| `taxRate`      | BigDecimal                 | Sí       | Tasa porcentual del impuesto                         |
| `taxInclusive` | boolean                    | Sí       | ¿El precio incluye el impuesto?                      |
| `name`         | string                     | No       | Nombre del producto                                  |
| `description`  | string                     | Sí       | Descripción detallada                                |
| `price`        | BigDecimal                 | No       | Precio final vigente calculado para el momento actual|
| `basePrice`    | BigDecimal                 | No       | Precio base sin descuentos o sin impuestos extras    |
| `prices`       | List<ProductPriceResponse> | No       | Detalle de todas las reglas de precios configuradas  |
| `imageUrl`     | string                     | Sí       | URL de imagen                                        |
| `isAvailable`  | boolean                    | No       | Si se encuentra disponible para ordenar              |
| `createdAt`    | string                     | No       | ISO 8601                                             |
| `updatedAt`    | string                     | No       | ISO 8601                                             |

---

## 4. Cart — Carrito de Compras

Cada cliente cuenta con un único carrito persistente por restaurante (`tenantId, customerId`).

---

### 📥 DTO: CartItemRequest

| Campo       | Tipo   | Requerido | Validación   | Descripción                  |
|-------------|--------|-----------|--------------|------------------------------|
| `productId` | UUID   | Sí        | UUID válido  | Producto a agregar           |
| `quantity`  | number | Sí        | ≥ 1          | Cantidad                     |
| `notes`     | string | No        | —            | Observaciones / preferencias |

### 📤 DTO: CartResponse

| Campo            | Tipo                   | Nullable | Descripción                                   |
|------------------|------------------------|----------|-----------------------------------------------|
| `id`             | UUID                   | No       | Identificador del carrito                     |
| `tenantId`       | UUID                   | No       | Restaurante                                   |
| `customerId`     | UUID                   | No       | Perfil de cliente en la tienda                |
| `customerUserId` | UUID                   | No       | Usuario global del cliente                    |
| `total`          | BigDecimal             | No       | Total acumulado de los ítems                  |
| `notes`          | string                 | Sí       | Notas generales                               |
| `items`          | List<CartItemResponse> | No       | Lista de productos agregados                  |
| `createdAt`      | string                 | No       | ISO 8601                                      |
| `updatedAt`      | string                 | No       | ISO 8601                                      |

### 📤 DTO: CartItemResponse

| Campo         | Tipo       | Nullable | Descripción                              |
|---------------|------------|----------|------------------------------------------|
| `id`          | UUID       | No       | Identificador del ítem en carrito        |
| `productId`   | UUID       | No       | Producto                                 |
| `productName` | string     | No       | Nombre del producto                      |
| `unitPrice`   | BigDecimal | No       | Precio unitario al momento de agregar    |
| `quantity`    | number     | No       | Cantidad seleccionada                    |
| `subtotal`    | BigDecimal | No       | Subtotal (quantity × unitPrice)          |
| `notes`       | string     | Sí       | Notas de preparación                     |

---

## 5. Order — Pedidos y Facturación

Módulo de órdenes de compra con control de estados, cobertura PostGIS y registro fiscal.

---

### 📥 DTO: OrderCreateRequest

| Campo               | Tipo                    | Requerido | Validación                             | Descripción                                |
|---------------------|-------------------------|-----------|----------------------------------------|--------------------------------------------|
| `tenantId`          | UUID                    | Sí        | UUID válido                            | Restaurante donde se realiza el pedido     |
| `deliveryType`      | string                  | Sí        | `DELIVERY`, `TAKEAWAY`, `DINE_IN`      | Modalidad de entrega                       |
| `customerAddressId` | UUID                    | No        | UUID válido                            | Dirección guardada seleccionada            |
| `deliveryAddress`   | string                  | No        | —                                      | Texto de la dirección de entrega           |
| `deliveryLatitude`  | number                  | No        | Double (WGS84)                         | Latitud de entrega                         |
| `deliveryLongitude` | number                  | No        | Double (WGS84)                         | Longitud de entrega                        |
| `notes`             | string                  | No        | —                                      | Indicaciones de preparación o entrega      |
| `items`             | List<OrderItemRequest>  | Sí        | Mínimo 1 ítem                          | Productos solicitados                      |

#### 📥 DTO: OrderItemRequest

| Campo       | Tipo   | Requerido | Validación  | Descripción                   |
|-------------|--------|-----------|-------------|-------------------------------|
| `productId` | UUID   | Sí        | UUID válido | Producto a comprar            |
| `quantity`  | number | Sí        | ≥ 1         | Cantidad                      |
| `notes`     | string | No        | —           | Observaciones para el cocinero|

---

### 📤 DTO: OrderResponse

| Campo              | Tipo                    | Nullable | Descripción                                               |
|--------------------|-------------------------|----------|-----------------------------------------------------------|
| `id`               | UUID                    | No       | Identificador del pedido                                  |
| `tenantId`         | UUID                    | No       | Restaurante                                               |
| `customerId`       | UUID                    | No       | ID de cliente en tienda                                   |
| `customerUserId`   | UUID                    | No       | ID de usuario global                                      |
| `customerFullName` | string                  | Sí       | Nombre del cliente                                        |
| `customerEmail`    | string                  | Sí       | Email del cliente                                         |
| `customerPhone`    | string                  | Sí       | Teléfono de contacto                                      |
| `deliveryStaffId`  | UUID                    | Sí       | ID del repartidor asignado                                |
| `orderNumber`      | string                  | No       | Código legible del pedido (ej. "ORD-000123")              |
| `deliveryType`     | string                  | No       | `DELIVERY`, `TAKEAWAY`, `DINE_IN`                         |
| `status`           | string                  | No       | `PENDIENTE`, `EN_PREPARACION`, etc.                       |
| `deliveryAddress`  | string                  | Sí       | Dirección histórica inmutable                             |
| `deliveryLatitude` | number                  | Sí       | Latitud de entrega                                        |
| `deliveryLongitude`| number                  | Sí       | Longitud de entrega                                       |
| `subtotal`         | BigDecimal              | No       | Suma de valores netos imponibles                          |
| `taxTotal`         | BigDecimal              | No       | Total de impuestos calculados (ej. IGV acumulado)         |
| `deliveryFee`      | BigDecimal              | No       | Costo de envío aplicado                                   |
| `total`            | BigDecimal              | No       | Monto total final a pagar                                 |
| `notes`            | string                  | Sí       | Notas del pedido                                          |
| `items`            | List<OrderItemResponse> | No       | Detalle de los platillos del pedido                       |
| `createdAt`        | string                  | No       | Fecha de creación (ISO 8601)                              |
| `updatedAt`        | string                  | No       | Fecha de actualización (ISO 8601)                         |

---

### 📤 DTO: OrderItemResponse (Snapshot Fiscal Inmutable)

| Campo         | Tipo       | Nullable | Descripción                                                        |
|---------------|------------|----------|--------------------------------------------------------------------|
| `id`          | UUID       | No       | Identificador del ítem                                             |
| `productId`   | UUID       | Sí       | ID del producto (conserva null si se elimina del catálogo posterior)|
| `productName` | string     | No       | **Snapshot inmutable** del nombre del producto                     |
| `taxId`       | UUID       | Sí       | Impuesto aplicado al momento de ordenar                            |
| `taxRate`     | BigDecimal | No       | **Snapshot inmutable** de la tasa porcentual (ej. 18.00)           |
| `taxAmount`   | BigDecimal | No       | **Snapshot inmutable** del monto de impuesto calculado             |
| `unitPrice`   | BigDecimal | No       | **Snapshot inmutable** del precio unitario cobrado                 |
| `quantity`    | number     | No       | Cantidad solicitada                                                |
| `subtotal`    | BigDecimal | No       | Subtotal (quantity × unitPrice)                                    |
| `notes`       | string     | Sí       | Instrucciones del ítem                                             |
| `createdAt`   | string     | No       | ISO 8601                                                           |
| `updatedAt`   | string     | No       | ISO 8601                                                           |

#### Ciclo de Vida del Pedido (`OrderStatus`):
```
PENDIENTE  ──>  EN_PREPARACION  ──>  LISTO_PARA_ENTREGA  ──>  EN_CAMINO  ──>  ENTREGADO
    │                 │                      │                     │
    └─────────────────┴────────── CANCELADO ─┴─────────────────────┘
```

> **🛡️ Regla Anti-Fraude:** El pedido solo puede pasar de `PENDIENTE` a `EN_PREPARACION` tras verificar que el pago asociado tiene el estado `APPROVED`.

---

## 6. Subscription — Suscripciones SaaS para Restaurantes

---

### 📥 DTO: SubscriptionPlanRequest

| Campo              | Tipo       | Requerido | Validación    | Descripción                                |
|--------------------|------------|-----------|---------------|--------------------------------------------|
| `name`             | string     | Sí        | Máx. 100 char | Nombre del plan (ej. "Plan Pro")           |
| `price`            | BigDecimal | Sí        | ≥ 0.00        | Tarifa mensual del plan                    |
| `billingCycleDays` | number     | Sí        | ≥ 1           | Días del ciclo de facturación (default: 30)|
| `maxProducts`      | number     | Sí        | ≥ 1           | Límite de productos en el catálogo         |
| `isActive`         | boolean    | No        | —             | Estado activo                              |

### 📤 DTO: SubscriptionPlanResponse

| Campo              | Tipo       | Nullable | Descripción                        |
|--------------------|------------|----------|------------------------------------|
| `id`               | UUID       | No       | ID del plan                        |
| `name`             | string     | No       | Nombre del plan                    |
| `price`            | BigDecimal | No       | Precio recurrente                  |
| `billingCycleDays` | number     | No       | Días del período                   |
| `maxProducts`      | number     | Sí       | Capacidad máxima de productos      |
| `isActive`         | boolean    | No       | Estado                             |

### 📥 DTO: SubscriptionCreateRequest

| Campo     | Tipo | Requerido | Descripción                 |
|-----------|------|-----------|-----------------------------|
| `planId`  | UUID | Sí        | ID del plan elegido         |
| `userId`  | UUID | Sí        | ID del usuario suscriptor   |

### 📤 DTO: SubscriptionResponse

| Campo             | Tipo                     | Nullable | Descripción                                               |
|-------------------|--------------------------|----------|-----------------------------------------------------------|
| `id`              | UUID                     | No       | ID de la suscripción                                      |
| `plan`            | SubscriptionPlanResponse | No       | Detalle del plan contratado                               |
| `userId`          | UUID                     | No       | Propietario de la cuenta                                  |
| `status`          | string                   | No       | `TRIAL`, `ACTIVE`, `PAST_DUE`, `CANCELED`, `EXPIRED`      |
| `startDate`       | string                   | No       | Fecha inicio (ISO 8601)                                   |
| `endDate`         | string                   | No       | Fecha fin del período (ISO 8601)                          |
| `mpPreapprovalId` | string                   | Sí       | ID de suscripción automática Mercado Pago                 |

---

## 7. Payment — Pagos y Mercado Pago

---

### 📥 DTO: PaymentCreateRequest

| Campo            | Tipo       | Requerido | Validación  | Descripción                               |
|------------------|------------|-----------|-------------|-------------------------------------------|
| `orderId`        | UUID       | Sí        | UUID válido | Pedido a pagar                            |
| `tenantId`       | UUID       | Sí        | UUID válido | Restaurante receptor                      |
| `mpPaymentId`    | string     | No        | —           | ID devuelto por el SDK de Mercado Pago    |
| `mpPreferenceId` | string     | No        | —           | ID de la preferencia de checkout          |
| `paymentMethod`  | string     | No        | —           | Método de pago (tarjeta, yape, etc.)      |
| `amount`         | BigDecimal | Sí        | ≥ 0.01      | Monto cobrado                             |
| `rawResponse`    | object     | No        | —           | JSON íntegro devuelto por el procesador   |

### 📤 DTO: PaymentResponse

| Campo            | Tipo          | Nullable | Descripción                                          |
|------------------|---------------|----------|------------------------------------------------------|
| `id`             | UUID          | No       | Identificador del pago                               |
| `orderId`        | UUID          | No       | Pedido vinculado                                     |
| `tenantId`       | UUID          | No       | Restaurante                                          |
| `mpPaymentId`    | string        | Sí       | ID en Mercado Pago                                   |
| `mpPreferenceId` | string        | Sí       | ID de Checkout Pro                                   |
| `paymentMethod`  | string        | Sí       | Canal utilizado                                      |
| `status`         | PaymentStatus | No       | `PENDING`, `APPROVED`, `REJECTED`, `REFUNDED`        |
| `amount`         | BigDecimal    | No       | Monto total pagado                                   |
| `createdAt`      | string        | No       | ISO 8601                                             |
| `updatedAt`      | string        | No       | ISO 8601                                             |

---

## 8. Inventory — Control de Almacén y Stock

---

### 📦 Modelos de Datos

#### `InventoryItem` (Insumos o Mercaderías)
- `id` (UUID): Identificador único.
- `tenantId` (UUID): Restaurante.
- `name` (string): Nombre del insumo o artículo (máx. 150).
- `sku` (string, opcional): Código de referencia de inventario.
- `unit` (string): Unidad de medida (`UNIT`, `KG`).
- `costPrice` (BigDecimal): Costo unitario promedio.
- `isActive` (boolean): Estado del insumo.

#### `InventoryStock` (Existencias en Almacén)
- `id` (UUID): Identificador único del registro de stock.
- `tenantId` (UUID): Restaurante.
- `itemId` (UUID): Insumo relacionado.
- `quantity` (BigDecimal): Existencia física actual.
- `reservedQuantity` (BigDecimal): Cantidad comprometida en pedidos en cocina.
- `minimumStock` (BigDecimal): Punto de reorden o alerta de stock crítico.
- `location` (string, opcional): Ubicación física dentro del almacén.

#### `InventoryMovement` (Kardex / Movimientos)
- `id` (UUID): Identificador del movimiento.
- `tenantId` (UUID): Restaurante.
- `movementType` (string): `ENTRY`, `EXIT`, `ADJUSTMENT`.
- `reason` (string): Motivo estandarizado:
  - **Entradas:** `PURCHASE`, `INITIAL_STOCK`, `CUSTOMER_RETURN`, `TRANSFER_IN`
  - **Salidas:** `KITCHEN_CONSUMPTION_OR_WASTE`, `EXPIRED_OR_SPOILAGE`, `DAMAGED_OR_LOSS`, `SALE`, `INTERNAL_CONSUMPTION`, `SUPPLIER_RETURN`, `TRANSFER_OUT`
  - **Ajustes:** `PHYSICAL_COUNT`, `CORRECTION`, `OTHER`
- `quantity` (BigDecimal): Cantidad movida.
- `previousQuantity` (BigDecimal): Saldo previo.
- `newQuantity` (BigDecimal): Saldo resultante.
- `performedBy` (UUID): Usuario que registró la operación.
- `reasonDetails` (string): Justificación textual obligatoria.

---

## 💻 Ejemplos de Integración para el Frontend

### 1. Iniciar Sesión en la Plataforma
```javascript
async function loginUser(email, password) {
  const response = await fetch('http://localhost:8080/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  });

  const res = await response.json();
  if (res.success) {
    localStorage.setItem('accessToken', res.data.accessToken);
    localStorage.setItem('refreshToken', res.data.refreshToken);
    console.log(`Sesión iniciada: ${res.data.user.fullName}`);
    return res.data;
  } else {
    throw new Error(res.message);
  }
}
```

### 2. Cargar Menú Público de un Restaurante por Slug
```javascript
async function loadStorefrontBySlug(slug) {
  const response = await fetch(`http://localhost:8080/api/v1/tenants/slug/${slug}`);
  const res = await response.json();

  if (res.success) {
    console.log(`Restaurante cargado: ${res.data.name}`, res.data);
    return res.data;
  } else {
    console.error('Error al cargar tienda:', res.message);
  }
}
```

### 3. Registro Contextual de Cliente en Storefront
```javascript
async function registerInStore(tenantSlug, customerData) {
  const response = await fetch(`http://localhost:8080/api/v1/store/${tenantSlug}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(customerData)
  });

  const res = await response.json();
  if (res.success) {
    localStorage.setItem('accessToken', res.data.accessToken);
    localStorage.setItem('refreshToken', res.data.refreshToken);
    console.log('Registrado en la tienda con éxito. Perfil:', res.data.customerProfile);
    return res.data;
  } else {
    throw new Error(res.message);
  }
}
```

### 4. Petición Autenticada con Manejo de Errores
```javascript
async function fetchMyRestaurant() {
  const token = localStorage.getItem('accessToken');
  const response = await fetch('http://localhost:8080/api/v1/tenants/me', {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });

  const result = await response.json();
  if (!response.ok) {
    if (response.status === 401) {
      console.warn('Token expirado, necesario refrescar');
    }
    throw new Error(result.message || 'Error en la petición');
  }

  return result.data;
}
```

---

## 📝 Notas Especiales para el Frontend

1. **Tokens en Headers:** Enviar siempre el token mediante el header HTTP `Authorization: Bearer <accessToken>`. No se usan cookies ni query params para el transporte de credenciales.
2. **UUIDs como Strings:** Todos los identificadores son cadenas UUID de 36 caracteres. No aplicar parseo a tipo número.
3. **Manejo de Fechas:** Las fechas viajan con zona horaria ISO 8601 (ej. `2026-10-06T14:30:00Z`). Para mostrarlas al usuario final, utilizar librerías como `date-fns` o `Intl.DateTimeFormat`.
4. **Geolocalización WGS84:** Las coordenadas geográficas utilizan el estándar WGS84 (SRID 4326): `latitude` y `longitude` como números en coma flotante (`Double`).
5. **CORS:** Durante el desarrollo local, los orígenes `http://localhost:3000` y `http://localhost:5173` están admitidos por defecto en el backend.
6. **Validación de Errores Jakarta:** En errores `400 Bad Request`, el campo `message` reúne las causas de validación concatenadas (ej. `"email: Formato de email inválido, password: La contraseña debe tener al menos 8 caracteres"`).
