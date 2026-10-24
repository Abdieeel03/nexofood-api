# Colección Bruno - NexoFood API

Esta carpeta contiene la colección de peticiones y pruebas automatizadas de **NexoFood API** lista para usar con [Bruno](https://www.usebruno.com/), actualizada con los cambios y endpoints de la **Fase 0, Fase 1, Fase 2 y Fase 3**.

---

## 🚀 Cómo Abrir la Colección en Bruno

1. Abre la aplicación de escritorio **Bruno**.
2. En la pantalla inicial, haz clic en **"Open Collection"**.
3. Selecciona la carpeta `bruno` de este repositorio.
4. En la esquina superior derecha, selecciona el entorno **`Local`** (o `Production`).

---

## 📂 Estructura de la Colección

```text
bruno/
├── bruno.json                                                # Configuración de la colección
├── environments/
│   ├── Local.bru                                             # baseUrl = http://localhost:8080 + variables dinámicas
│   └── Production.bru                                        # baseUrl = https://api.nexofood.lat + variables dinámicas
├── Identity/
│   ├── 01 - Registro de Usuario.bru                          # POST   /api/v1/auth/register (201 Created)
│   ├── 02 - Inicio de Sesion (Login).bru                     # POST   /api/v1/auth/login (200 OK)
│   ├── 03 - Renovar Token (Refresh).bru                      # POST   /api/v1/auth/refresh (200 OK)
│   ├── 04 - Error Registro - Email Invalido (400).bru
│   ├── 05 - Error Registro - Email Duplicado (409).bru
│   ├── 06 - Error Login - Password Incorrecto (401).bru
│   ├── 07 - Mi Perfil (Get Me).bru                           # GET    /api/v1/auth/me (200 OK)
│   ├── 08 - Actualizar Mi Perfil.bru                         # PUT    /api/v1/users/me (200 OK)
│   ├── 09 - Cambiar Mi Contrasena.bru                        # PATCH  /api/v1/users/me/password (200 OK)
│   ├── 10 - Crear Direccion.bru                              # POST   /api/v1/users/me/addresses (201 Created)
│   ├── 11 - Listar Mis Direcciones.bru                       # GET    /api/v1/users/me/addresses (200 OK)
│   ├── 12 - Actualizar Direccion.bru                         # PUT    /api/v1/users/me/addresses/{{addressId}} (200 OK)
│   ├── 13 - Eliminar Direccion.bru                           # DELETE /api/v1/users/me/addresses/{{addressId}} (200 OK)
│   ├── 14 - Cerrar Sesion (Logout).bru                       # POST   /api/v1/auth/logout (200 OK)
│   ├── 15 - Eliminar Mi Cuenta (Soft Delete).bru             # DELETE /api/v1/users/me (200 OK)
│   ├── 16 - Admin - Listar Usuarios (SUPERADMIN).bru         # GET    /api/v1/admin/users (200 OK)
│   └── 17 - Admin - Cambiar Estado Usuario (SUPERADMIN).bru  # PATCH  /api/v1/admin/users/{{userId}}/status (200 OK)
├── Subscription/
│   ├── 01 - Listar Planes Activos (Publico).bru              # GET    /api/v1/plans (200 OK - público)
│   ├── 02 - Admin - Crear Plan de Suscripcion (SUPERADMIN).bru # POST /api/v1/admin/plans (201 Created)
│   ├── 03 - Admin - Listar Todos los Planes (SUPERADMIN).bru # GET    /api/v1/admin/plans (200 OK)
│   ├── 04 - Admin - Obtener Plan por ID (SUPERADMIN).bru     # GET    /api/v1/admin/plans/{{planId}} (200 OK)
│   ├── 05 - Admin - Actualizar Plan (SUPERADMIN).bru         # PUT    /api/v1/admin/plans/{{planId}} (200 OK)
│   ├── 06 - Admin - Cambiar Estado del Plan (SUPERADMIN).bru # PATCH  /api/v1/admin/plans/{{planId}}/status (200 OK)
│   ├── 07 - Iniciar Suscripcion Trial (Start Trial).bru      # POST   /api/v1/subscriptions (201 Created - userId desde JWT)
│   ├── 08 - Obtener Mi Suscripcion (Get My Subscription).bru # GET    /api/v1/subscriptions/me (200 OK)
│   └── 09 - Cancelar Mi Suscripcion (Cancel Subscription).bru # PATCH /api/v1/subscriptions/me/cancel (200 OK)
└── Store/
    ├── 01 - Crear Restaurante (Tenant).bru                   # POST   /api/v1/tenants (201 Created - owner y sub desde JWT)
    ├── 02 - Obtener Mi Restaurante (Get My Tenant).bru       # GET    /api/v1/tenants/me (200 OK)
    ├── 03 - Obtener Restaurante por Slug (Publico).bru       # GET    /api/v1/tenants/slug/{{tenantSlug}} (200 OK)
    ├── 04 - Obtener Restaurante por ID (Miembro).bru         # GET    /api/v1/tenants/{{tenantId}} (200 OK - @tenantAccess.isMember)
    ├── 05 - Actualizar Restaurante (Solo OWNER).bru          # PUT    /api/v1/tenants/{{tenantId}} (200 OK - @tenantAccess.isOwner)
    ├── 06 - Cambiar Estado Restaurante (Solo OWNER).bru      # PATCH  /api/v1/tenants/{{tenantId}}/status (200 OK - DTO validado)
    ├── 07 - Listar Miembros del Staff (OWNER o ADMIN).bru    # GET    /api/v1/tenants/{{tenantId}}/members (200 OK)
    ├── 08 - Agregar Miembro al Staff (OWNER o ADMIN).bru     # POST   /api/v1/tenants/{{tenantId}}/members (201 Created)
    ├── 09 - Cambiar Rol de Miembro (OWNER o ADMIN).bru       # PATCH  /api/v1/tenants/{{tenantId}}/members/{{memberId}}/role (200 OK)
    ├── 10 - Eliminar Miembro del Staff (OWNER o ADMIN).bru   # DELETE /api/v1/tenants/{{tenantId}}/members/{{memberId}} (200 OK)
    ├── 11 - Store Auth - Registro de Cliente en Tienda.bru   # POST   /api/v1/store/{{tenantSlug}}/auth/register (201 Created)
    ├── 12 - Store Auth - Login de Cliente en Tienda.bru      # POST   /api/v1/store/{{tenantSlug}}/auth/login (200 OK)
    ├── 13 - Admin Clientes - Listar Clientes (OWNER o ADMIN).bru
    ├── 14 - Admin Clientes - Ver Perfil Cliente (OWNER o ADMIN).bru
    ├── 15 - Admin Clientes - Bloquear o Desbloquear Cliente.bru
    ├── 16 - Admin Clientes - Actualizar Notas Privadas.bru
    ├── 17 - Admin Clientes - Ajustar Puntos de Fidelidad.bru
    └── 18 - Error Acceso A Otro Tenant No Autorizado (403).bru # PUT  /api/v1/tenants/{otroId} (403 Forbidden)
```

---

## ⚡ Automatización de Variables y Tokens

* **Autenticación (`01 - Registro` / `02 - Login` / `07 - Mi Perfil`):** guardan automáticamente `accessToken`, `refreshToken` y `userId` en el entorno activo.
* **Direcciones (`10 - Crear Direccion`):** guarda automáticamente `addressId` para usarlo en actualización (`12`) y eliminación (`13`).
* **Suscripciones (`01 - Listar Planes` / `02 - Crear Plan` / `07 - Iniciar Trial`):** guardan automáticamente `planId` y `subscriptionId`.
* **Restaurantes (`01 - Crear Restaurante` / `02 - Obtener Mi Restaurante`):** guardan `tenantId` y `tenantSlug` (y resuelven la suscripción vigente automáticamente sin pedir `subscriptionId` en el body).
* **Equipo y Clientes (`08 - Agregar Miembro` / `11-12 - Store Auth`):** guardan `memberId` y `customerId` para las rutas de administración de staff y clientes.
* Cada petición incluye aserciones automáticas en la pestaña **Tests**.
