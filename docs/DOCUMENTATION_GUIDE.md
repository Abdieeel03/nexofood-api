# 📘 Guía de Documentación de API para Frontend

> **Proyecto:** NexoFood API  
> **Stack:** Java 21 · Spring Boot 4.1.1 · PostgreSQL 16 + PostGIS  
> **Versión:** 1.0  
> **Última actualización:** 2026-09-26

---

## 📌 Propósito

Esta guía establece las **reglas y convenciones** para documentar los endpoints de la API REST de NexoFood. Su objetivo es que cualquier desarrollador frontend pueda integrar la API de forma clara, rápida y sin ambigüedades.

Todo documento de API que se cree para este proyecto **debe seguir las reglas definidas aquí**.

---

## 📐 Estructura General del Documento

Todo archivo de documentación de API debe seguir esta estructura:

```
1. Título y metadata (nombre del proyecto, versión, base URL, stack)
2. Información general (autenticación, formato de datos, manejo de errores)
3. Endpoints agrupados por módulo (Bounded Context)
4. Modelos de datos / DTOs (schemas de request y response)
5. Códigos de estado HTTP utilizados
6. Notas adicionales para el frontend
```

---

## 🏷️ Regla 1: Información Base

Todo documento debe comenzar con una sección que incluya:

| Campo                | Descripción                                             | Ejemplo                          |
|----------------------|---------------------------------------------------------|----------------------------------|
| **Base URL**         | URL raíz de la API                                      | `http://localhost:8080/api/v1`   |
| **Base URL (Prod)**  | URL de producción                                       | `https://api.nexofood.lat/api/v1`|
| **Formato**          | Tipo de contenido aceptado y retornado                  | `application/json`               |
| **Autenticación**    | Mecanismo de autenticación utilizado                    | JWT Bearer Token en header       |
| **Versión**          | Versión actual de la API                                | `v1`                             |
| **Documentación viva** | URL de Swagger UI                                     | `/swagger-ui/index.html`        |

---

## 🔐 Regla 2: Documentar la Autenticación

Se debe explicar de manera detallada:

1. **Cómo se obtienen los tokens** (endpoint de register/login).
2. **Dónde se almacenan en el frontend** (localStorage, sessionStorage, estado de la app).
3. **Cómo se envían en cada request** (header `Authorization: Bearer <token>`).
4. **Cómo se renuevan** (endpoint de refresh con el refreshToken en el body).
5. **Qué pasa cuando expiran** (código de error, flujo de renovación).
6. **Rotación y revocación** de refresh tokens.

### Formato obligatorio:

```markdown
### 🔑 Autenticación

- **Tipo:** JWT (JSON Web Token) con firma HMAC-SHA256
- **Envío:** Header `Authorization: Bearer <accessToken>`
- **Access Token:** Expira en 15 minutos
- **Refresh Token:** Expira en 7 días, con rotación automática
- **Renovación:** POST `/api/v1/auth/refresh` enviando `{ "refreshToken": "..." }`
- **Expiración:** El servidor responde con `401 Unauthorized`
- **Seguridad:** Si se detecta reutilización de un refresh token revocado, se revocan TODOS los tokens del usuario
```

> **⚠️ Nota para el frontend:**
> - El token se envía en el header `Authorization`, NO en cookies.
> - El frontend es responsable de almacenar los tokens de forma segura.
> - Configurar `Authorization: Bearer ${accessToken}` en cada request autenticada.

---

## 📡 Regla 3: Documentar Cada Endpoint

Cada endpoint debe documentarse con la siguiente plantilla:

### Plantilla de Endpoint

```markdown
### MÉTODO `/ruta/completa`

> Breve descripción de qué hace este endpoint.

**Autenticación:** Requerida / No requerida  
**Rol requerido:** SUPERADMIN / USER / Cualquiera  
**Content-Type:** `application/json`

#### 📥 Request

**Parámetros de URL:**

| Parámetro | Tipo   | Requerido | Descripción          |
|-----------|--------|-----------|----------------------|
| `id`      | UUID   | Sí        | ID del recurso       |

**Query Parameters:**

| Parámetro    | Tipo   | Requerido | Descripción              |
|--------------|--------|-----------|--------------------------|
| `tenantId`   | UUID   | No        | Filtrar por restaurante  |

**Body (JSON):**

| Campo      | Tipo       | Requerido | Validación          | Descripción              |
|------------|------------|-----------|---------------------|--------------------------|
| `name`     | string     | Sí        | max 150 caracteres  | Nombre del recurso       |
| `email`    | string     | Sí        | Formato email válido| Correo electrónico       |
| `price`    | BigDecimal | Sí        | ≥ 0.01              | Precio del producto      |

**Ejemplo de request:**
```json
{
  "name": "Hamburguesa Clásica",
  "price": 8.99
}
```

#### 📤 Response

**Éxito (200 | 201) — Envelope `ApiResponse<T>`:**
```json
{
  "success": true,
  "message": "Descripción del éxito",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "Hamburguesa Clásica"
  }
}
```

**Error (4xx | 5xx) — Envelope `ErrorResponse`:**
```json
{
  "message": "Descripción del error",
  "error": "NOMBRE_DEL_ERROR",
  "status": 400,
  "timestamp": "2026-09-26T14:00:00",
  "path": "/api/v1/recurso",
  "method": "POST"
}
```
```

---

## 📊 Regla 4: Documentar los Envelopes de Respuesta

La API utiliza **dos envelopes estándar** para todas las respuestas. Estos deben documentarse siempre:

### Envelope de éxito: `ApiResponse<T>`

```json
{
  "success": true,
  "message": "Mensaje descriptivo del resultado",
  "data": { }
}
```

| Campo     | Tipo    | Descripción                                     |
|-----------|---------|-------------------------------------------------|
| `success` | boolean | Siempre `true` en respuestas exitosas           |
| `message` | string  | Mensaje legible para el usuario o logs           |
| `data`    | T       | Payload del recurso (varía según el endpoint)    |

### Envelope de error: `ErrorResponse`

```json
{
  "message": "Descripción del error",
  "error": "TIPO_DE_ERROR",
  "status": 400,
  "timestamp": "2026-09-26T14:00:00",
  "path": "/api/v1/ruta",
  "method": "POST"
}
```

| Campo       | Tipo   | Descripción                                          |
|-------------|--------|------------------------------------------------------|
| `message`   | string | Descripción legible del error                        |
| `error`     | string | Tipo de error HTTP (BAD_REQUEST, UNAUTHORIZED, etc.) |
| `status`    | number | Código de estado HTTP                                |
| `timestamp` | string | Fecha y hora del error (ISO 8601)                    |
| `path`      | string | Ruta del endpoint que generó el error                |
| `method`    | string | Método HTTP de la petición                           |

---

## 📦 Regla 5: Documentar los DTOs (Request y Response)

Se deben documentar los DTOs de entrada y salida por separado:

### Formato para DTOs de Request

```markdown
### 📥 DTO: NombreRequest

| Campo      | Tipo       | Requerido | Validación               | Descripción              |
|------------|------------|-----------|--------------------------|--------------------------|
| `email`    | string     | Sí        | Email válido, max 255    | Correo electrónico       |
| `password` | string     | Sí        | min 8, max 100           | Contraseña del usuario   |
| `fullName` | string     | Sí        | max 150                  | Nombre completo          |
| `phone`    | string     | No        | max 20                   | Teléfono de contacto     |
```

### Formato para DTOs de Response

```markdown
### 📤 DTO: NombreResponse

| Campo       | Tipo          | Nullable | Descripción                              |
|-------------|---------------|----------|------------------------------------------|
| `id`        | UUID (string) | No       | Identificador único                      |
| `email`     | string        | No       | Correo electrónico del usuario           |
| `fullName`  | string        | No       | Nombre completo                          |
| `systemRole`| string        | No       | Rol del sistema: `"SUPERADMIN"` o `"USER"` |
| `isActive`  | boolean       | No       | Si el recurso está activo                |
| `createdAt` | string        | No       | Fecha de creación (ISO 8601 con offset)  |
| `updatedAt` | string        | No       | Fecha de actualización (ISO 8601)        |
```

> **📝 Nota sobre IDs:** Todos los IDs en NexoFood son de tipo **UUID** (formato: `3fa85f64-5717-4562-b3fc-2c963f66afa6`). El frontend debe tratarlos como `string`.

> **📝 Nota sobre fechas:** Todas las fechas se serializan en formato **ISO 8601** (ej: `2026-09-26T14:00:00Z`), nunca como timestamps numéricos.

---

## ❌ Regla 6: Documentar Errores de Forma Consistente

### Tabla de códigos HTTP obligatoria

Toda documentación debe incluir esta tabla:

| Código | Error              | Cuándo se retorna                                              |
|--------|--------------------|----------------------------------------------------------------|
| `200`  | OK                 | Operación exitosa (lectura, actualización, login, refresh)     |
| `201`  | Created            | Recurso creado exitosamente (registro)                         |
| `400`  | Bad Request        | Error de validación de campos (`MethodArgumentNotValidException`) |
| `401`  | Unauthorized       | Token ausente, inválido, expirado o credenciales incorrectas   |
| `403`  | Forbidden          | Sin permisos suficientes o cuenta desactivada                  |
| `404`  | Not Found          | Recurso no encontrado                                          |
| `409`  | Conflict           | Recurso duplicado (ej: email ya registrado)                    |
| `500`  | Internal Server Error | Error interno del servidor                                  |

### Formato de error de validación (Jakarta Validation)

Cuando falla la validación de campos, el `message` incluye los campos y sus errores concatenados:

```json
{
  "message": "Error de validación: email: Formato de email inválido, password: La contraseña es obligatoria",
  "error": "BAD_REQUEST",
  "status": 400,
  "timestamp": "2026-09-26T14:00:00",
  "path": "/api/v1/auth/register",
  "method": "POST"
}
```

---

## 🔗 Regla 7: Agrupar Endpoints por Módulo (Bounded Context)

Los endpoints deben organizarse agrupados por módulo de dominio, siguiendo este orden:

1. **Identity** (Autenticación) — registro, login, refresh, gestión de usuarios, direcciones
2. **Subscription** (Suscripciones) — planes SaaS, suscripciones
3. **Tenant** (Restaurantes) — CRUD de tenants, miembros y roles
4. **Catalog** (Catálogo) — categorías y productos del restaurante
5. **Cart** (Carrito) — carrito de compras por cliente por tenant
6. **Order** (Pedidos) — creación de pedidos, estados, delivery
7. **Payment** (Pagos) — transacciones, Mercado Pago, webhooks
8. **Inventory** (Inventario) — items, stock, movimientos

Dentro de cada grupo, los endpoints se ordenan:

1. `GET` (listar todos)
2. `GET /:id` (obtener uno)
3. `POST` (crear)
4. `PUT /:id` o `PATCH /:id` (actualizar)
5. `DELETE /:id` (eliminar)

---

## 🧩 Regla 8: Incluir Ejemplos de Integración con el Frontend

Cada sección de módulo debe incluir al menos un ejemplo de cómo consumir el endpoint desde el frontend usando `fetch` o `axios`.

### Formato obligatorio:

```markdown
#### 💻 Ejemplo de integración (fetch)

```javascript
// Ejemplo: Login
const response = await fetch('http://localhost:8080/api/v1/auth/login', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    email: 'usuario@ejemplo.com',
    password: 'Password123!'
  })
});

const data = await response.json();

if (data.success) {
  // Guardar tokens
  localStorage.setItem('accessToken', data.data.accessToken);
  localStorage.setItem('refreshToken', data.data.refreshToken);
  console.log('Bienvenido', data.data.user.fullName);
} else {
  console.error(data.message);
}
```

#### 💻 Ejemplo de petición autenticada (fetch)

```javascript
const token = localStorage.getItem('accessToken');

const response = await fetch('http://localhost:8080/api/v1/recurso', {
  method: 'GET',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`
  }
});
```
```

---

## 🏗️ Regla 9: Documentar la Arquitectura Multi-Tenant

NexoFood es una plataforma **SaaS multi-tenant**. Esto afecta al frontend de forma importante:

1. **Cada restaurante es un `Tenant`** con su propio catálogo, pedidos, inventario y staff.
2. **Un usuario puede ser dueño de un solo tenant** (relación 1 a 1 estricta).
3. **Los productos y categorías pertenecen a un tenant específico**.
4. **Los carritos son únicos por combinación `(tenant, customer)`** — un cliente tiene un carrito por restaurante.
5. **Los pedidos validan cobertura geoespacial** — si la dirección del cliente está fuera del radio de delivery, el servidor rechaza el pedido.

### Formato obligatorio para endpoints multi-tenant:

```markdown
> **🏪 Multi-Tenant:** Este endpoint opera en el contexto de un tenant (restaurante) específico.
> El `tenantId` se obtiene al seleccionar un restaurante en la interfaz.
```

---

## 📋 Regla 10: Notas Especiales para el Frontend

Al final de cada sección de módulo, incluir una sección de "Notas para el Frontend":

```markdown
> **📝 Notas para el Frontend:**
> - Todos los IDs son **UUID** (strings de 36 caracteres). No intentar parsearlos como números.
> - Las fechas llegan en formato **ISO 8601** (ej: `2026-09-26T14:00:00Z`).
> - El token se envía en el header `Authorization: Bearer <token>`, NO en cookies.
> - Las respuestas exitosas siempre tienen `success: true` y el payload en `data`.
> - Las respuestas de error tienen `message`, `error`, `status`, `timestamp`, `path` y `method`.
> - Los coordenadas geográficas usan **WGS84 (SRID 4326)**: `latitude` y `longitude` como `Double`.
> - Swagger UI está disponible en `/swagger-ui/index.html` para explorar y probar endpoints.
```

---

## ✅ Checklist de Documentación

Antes de finalizar cualquier documento de API, verificar que cumple con:

- [ ] Incluye Base URL (local y producción) y metadata del proyecto
- [ ] Documenta el mecanismo de autenticación JWT Bearer completo
- [ ] Cada endpoint tiene: método, ruta, descripción, auth, body, response con envelopes
- [ ] Los DTOs de request y response están documentados con tipos y validaciones
- [ ] Los envelopes `ApiResponse<T>` y `ErrorResponse` están explicados
- [ ] Los errores están documentados con formato y códigos HTTP (incluyendo 409)
- [ ] Los endpoints están agrupados por módulo y ordenados por CRUD
- [ ] Incluye al menos un ejemplo de integración con fetch/axios por módulo
- [ ] La naturaleza multi-tenant del sistema está documentada
- [ ] Incluye notas especiales para el frontend (UUIDs, fechas ISO 8601, Bearer token)
- [ ] Indica qué endpoints están activos vs. preparados para futuro

---

> **📌 Este documento es la fuente de verdad para la documentación de la API de NexoFood. Todo archivo `API_DOCUMENTATION.md` u otro documento de referencia para el frontend debe seguir estas reglas al pie de la letra.**
