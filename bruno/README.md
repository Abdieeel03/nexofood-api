# Colección Bruno - NexoFood API

Esta carpeta contiene la colección de peticiones y pruebas automatizadas de **NexoFood API** lista para usar con [Bruno](https://www.usebruno.com/), actualizada con los cambios y endpoints de la **Fase 0 a Fase 7** (123 requests en 9 módulos + entornos).

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
│   └── Production.bru                                        # baseUrl = https://api.nexofood-app.com + variables dinámicas
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
│   ├── 09 - Cancelar Mi Suscripcion (Cancel Subscription).bru # PATCH /api/v1/subscriptions/me/cancel (200 OK)
│   ├── 10 - Iniciar Checkout Suscripcion Preaprobada MP.bru # POST   /api/v1/subscriptions/checkout (201 Created)
│   └── 11 - Listar Mis Cobros de Suscripcion.bru             # GET    /api/v1/subscriptions/me/payments (200 OK)
├── Store/
│   ├── 01 - Crear Restaurante (Tenant).bru                   # POST   /api/v1/tenants (201 Created - owner y sub desde JWT)
│   ├── 02 - Obtener Mi Restaurante (Get My Tenant).bru       # GET    /api/v1/tenants/me (200 OK)
│   ├── 03 - Obtener Restaurante por Slug (Publico).bru       # GET    /api/v1/tenants/slug/{{tenantSlug}} (200 OK)
│   ├── 04 - Obtener Restaurante por ID (Miembro).bru         # GET    /api/v1/tenants/{{tenantId}} (200 OK - @tenantAccess.isMember)
│   ├── 05 - Actualizar Restaurante (Solo OWNER).bru          # PUT    /api/v1/tenants/{{tenantId}} (200 OK - @tenantAccess.isOwner)
│   ├── 06 - Cambiar Estado Restaurante (Solo OWNER).bru      # PATCH  /api/v1/tenants/{{tenantId}}/status (200 OK - DTO validado)
│   ├── 07 - Listar Miembros del Staff (OWNER o ADMIN).bru    # GET    /api/v1/tenants/{{tenantId}}/members (200 OK)
│   ├── 08 - Agregar Miembro al Staff (OWNER o ADMIN).bru     # POST   /api/v1/tenants/{{tenantId}}/members (201 Created)
│   ├── 09 - Cambiar Rol de Miembro (OWNER o ADMIN).bru       # PATCH  /api/v1/tenants/{{tenantId}}/members/{{memberId}}/role (200 OK)
│   ├── 10 - Eliminar Miembro del Staff (OWNER o ADMIN).bru   # DELETE /api/v1/tenants/{{tenantId}}/members/{{memberId}} (200 OK)
│   ├── 11 - Store Auth - Registro de Cliente en Tienda.bru   # POST   /api/v1/store/{{tenantSlug}}/auth/register (201 Created)
│   ├── 12 - Store Auth - Login de Cliente en Tienda.bru      # POST   /api/v1/store/{{tenantSlug}}/auth/login (200 OK)
│   ├── 13 - Admin Clientes - Listar Clientes (OWNER o ADMIN).bru
│   ├── 14 - Admin Clientes - Ver Perfil Cliente (OWNER o ADMIN).bru
│   ├── 15 - Admin Clientes - Bloquear o Desbloquear Cliente.bru
│   ├── 16 - Admin Clientes - Actualizar Notas Privadas.bru
│   ├── 17 - Admin Clientes - Ajustar Puntos de Fidelidad.bru
│   ├── 18 - Error Acceso A Otro Tenant No Autorizado (403).bru # PUT  /api/v1/tenants/{otroId} (403 Forbidden)
│   ├── 19 - Storefront - Obtener Menu Publico por Slug (Sin Token).bru # GET /api/v1/store/{{tenantSlug}}/menu (200 OK)
│   ├── 20 - Storefront - Listar Categorias Activas de Tienda (Sin Token).bru # GET /api/v1/store/{{tenantSlug}}/categories (200 OK)
│   ├── 21 - Storefront - Listar Productos Disponibles Paginados (Sin Token).bru # GET /api/v1/store/{{tenantSlug}}/products (200 OK)
│   ├── 22 - Storefront - Obtener Detalle Publico de Producto (Sin Token).bru # GET /api/v1/store/{{tenantSlug}}/products/{{productId}} (200 OK)
│   └── 23 - Error Crear Restaurante Sin Suscripcion (400).bru # POST /api/v1/tenants (400 Bad Request)
├── Catalog/
│   ├── 01 - Taxes - Crear Impuesto (OWNER o ADMIN).bru         # POST   /api/v1/tenants/{{tenantId}}/taxes (201 Created)
│   ├── 02 - Taxes - Listar Impuestos (Miembro).bru             # GET    /api/v1/tenants/{{tenantId}}/taxes (200 OK)
│   ├── 03 - Taxes - Obtener Impuesto por ID (Miembro).bru      # GET    /api/v1/tenants/{{tenantId}}/taxes/{{taxId}} (200 OK)
│   ├── 04 - Taxes - Actualizar Impuesto (OWNER o ADMIN).bru    # PUT    /api/v1/tenants/{{tenantId}}/taxes/{{taxId}} (200 OK)
│   ├── 05 - Taxes - Cambiar Estado Impuesto (OWNER o ADMIN).bru # PATCH /api/v1/tenants/{{tenantId}}/taxes/{{taxId}}/status (200 OK)
│   ├── 06 - Categories - Crear Categoria (OWNER o ADMIN).bru   # POST   /api/v1/tenants/{{tenantId}}/categories (201 Created)
│   ├── 07 - Categories - Listar Categorias (Miembro).bru       # GET    /api/v1/tenants/{{tenantId}}/categories (200 OK)
│   ├── 08 - Categories - Obtener Categoria por ID (Miembro).bru # GET   /api/v1/tenants/{{tenantId}}/categories/{{categoryId}} (200 OK)
│   ├── 09 - Categories - Actualizar Categoria (OWNER o ADMIN).bru # PUT /api/v1/tenants/{{tenantId}}/categories/{{categoryId}} (200 OK)
│   ├── 10 - Categories - Reordenar Categoria (OWNER o ADMIN).bru # PATCH /api/v1/tenants/{{tenantId}}/categories/{{categoryId}}/sort-order (200 OK)
│   ├── 11 - Categories - Cambiar Estado Categoria (OWNER o ADMIN).bru # PATCH /api/v1/tenants/{{tenantId}}/categories/{{categoryId}}/status (200 OK)
│   ├── 12 - Products - Crear Producto (OWNER o ADMIN).bru      # POST   /api/v1/tenants/{{tenantId}}/products (201 Created)
│   ├── 13 - Products - Listar Productos Paginados (Miembro).bru # GET   /api/v1/tenants/{{tenantId}}/products (200 OK)
│   ├── 14 - Products - Obtener Producto por ID (Miembro).bru   # GET    /api/v1/tenants/{{tenantId}}/products/{{productId}} (200 OK)
│   ├── 15 - Products - Actualizar Producto (OWNER o ADMIN).bru # PUT    /api/v1/tenants/{{tenantId}}/products/{{productId}} (200 OK)
│   ├── 16 - Products - Cambiar Disponibilidad (OWNER o ADMIN).bru # PATCH /api/v1/tenants/{{tenantId}}/products/{{productId}}/availability (200 OK)
│   ├── 17 - Prices - Agregar Regla de Precio Promocional (OWNER o ADMIN).bru # POST /api/v1/tenants/{{tenantId}}/products/{{productId}}/prices (201 Created)
│   ├── 18 - Prices - Listar Reglas de Precio del Producto (Miembro).bru # GET /api/v1/tenants/{{tenantId}}/products/{{productId}}/prices (200 OK)
│   ├── 19 - Prices - Actualizar Regla de Precio (OWNER o ADMIN).bru # PUT /api/v1/tenants/{{tenantId}}/products/{{productId}}/prices/{{priceId}} (200 OK)
│   ├── 20 - Prices - Eliminar Regla de Precio Promocional (OWNER o ADMIN).bru # DELETE /api/v1/tenants/{{tenantId}}/products/{{productId}}/prices/{{priceId}} (200 OK)
│   ├── 21 - Products - Deshabilitar Producto Soft Delete (OWNER o ADMIN).bru # DELETE /api/v1/tenants/{{tenantId}}/products/{{productId}} (200 OK)
│   ├── 22 - Taxes - Eliminar Impuesto (OWNER o ADMIN).bru    # DELETE /api/v1/tenants/{{tenantId}}/taxes/{{taxId}} (200 OK)
│   ├── 23 - Categories - Eliminar Categoria (OWNER o ADMIN).bru # DELETE /api/v1/tenants/{{tenantId}}/categories/{{categoryId}} (200 OK)
│   └── 24 - Prices - Obtener Regla de Precio por ID (Miembro).bru # GET /api/v1/tenants/{{tenantId}}/products/{{productId}}/prices/{{priceId}} (200 OK)
├── Inventory/
│   ├── 01 - Items - Crear Insumo (OWNER o ADMIN).bru         # POST   /api/v1/tenants/{{tenantId}}/inventory/items (201 Created)
│   ├── 02 - Items - Listar Insumos Paginados (Miembro).bru   # GET    /api/v1/tenants/{{tenantId}}/inventory/items (200 OK)
│   ├── 03 - Items - Obtener Insumo por ID (Miembro).bru      # GET    /api/v1/tenants/{{tenantId}}/inventory/items/{{inventoryItemId}} (200 OK)
│   ├── 04 - Items - Actualizar Insumo (OWNER o ADMIN).bru    # PUT    /api/v1/tenants/{{tenantId}}/inventory/items/{{inventoryItemId}} (200 OK)
│   ├── 05 - Items - Cambiar Estado Insumo (OWNER o ADMIN).bru # PATCH /api/v1/tenants/{{tenantId}}/inventory/items/{{inventoryItemId}}/status (200 OK)
│   ├── 06 - Items - Eliminar Insumo (OWNER o ADMIN).bru      # DELETE /api/v1/tenants/{{tenantId}}/inventory/items/{{inventoryItemId}} (200 OK)
│   ├── 07 - Stocks - Crear Stock para Insumo (OWNER o ADMIN).bru # POST /api/v1/tenants/{{tenantId}}/inventory/stocks (201 Created)
│   ├── 08 - Stocks - Listar Existencias Paginadas (Miembro).bru # GET /api/v1/tenants/{{tenantId}}/inventory/stocks (200 OK)
│   ├── 09 - Stocks - Listar Alertas de Stock Bajo (Miembro).bru # GET /api/v1/tenants/{{tenantId}}/inventory/stocks/low-stock (200 OK)
│   ├── 10 - Stocks - Obtener Stock por ID (Miembro).bru      # GET    /api/v1/tenants/{{tenantId}}/inventory/stocks/{{stockId}} (200 OK)
│   ├── 11 - Stocks - Obtener Stock por ID de Insumo (Miembro).bru # GET /api/v1/tenants/{{tenantId}}/inventory/stocks/item/{{inventoryItemId}} (200 OK)
│   ├── 12 - Stocks - Actualizar Umbrales de Stock (OWNER o ADMIN).bru # PUT /api/v1/tenants/{{tenantId}}/inventory/stocks/{{stockId}} (200 OK)
│   ├── 13 - Stocks - Consultar Kardex del Stock (Miembro).bru # GET   /api/v1/tenants/{{tenantId}}/inventory/stocks/{{stockId}}/kardex (200 OK)
│   ├── 14 - Movements - Registrar Movimiento Entrada o Salida (OWNER, ADMIN o COCINERO).bru # POST /api/v1/tenants/{{tenantId}}/inventory/movements (201 Created)
│   ├── 15 - Movements - Listar Movimientos de Inventario (Miembro).bru # GET /api/v1/tenants/{{tenantId}}/inventory/movements (200 OK)
│   ├── 16 - Recipes - Listar Ingredientes del Producto (Miembro).bru # GET /api/v1/tenants/{{tenantId}}/products/{{productId}}/ingredients (200 OK)
│   ├── 17 - Recipes - Agregar o Actualizar Ingrediente en Receta (OWNER o ADMIN).bru # POST /api/v1/tenants/{{tenantId}}/products/{{productId}}/ingredients (201 Created)
│   └── 18 - Recipes - Eliminar Ingrediente de Receta (OWNER o ADMIN).bru # DELETE /api/v1/tenants/{{tenantId}}/products/{{productId}}/ingredients/{{ingredientId}} (200 OK)
├── Cart/
│   ├── 01 - Obtener Mi Carrito en Tienda.bru                   # GET    /api/v1/store/{{tenantSlug}}/cart (200 OK)
│   ├── 02 - Agregar Producto al Carrito.bru                    # POST   /api/v1/store/{{tenantSlug}}/cart/items (200 OK)
│   ├── 03 - Actualizar Cantidad o Notas de Item.bru            # PATCH  /api/v1/store/{{tenantSlug}}/cart/items/{{cartItemId}} (200 OK)
│   ├── 04 - Actualizar Notas Generales del Carrito.bru         # PATCH  /api/v1/store/{{tenantSlug}}/cart/notes (200 OK)
│   ├── 05 - Eliminar Item del Carrito.bru                      # DELETE /api/v1/store/{{tenantSlug}}/cart/items/{{cartItemId}} (200 OK)
│   └── 06 - Vaciar Carrito de la Tienda.bru                    # DELETE /api/v1/store/{{tenantSlug}}/cart (200 OK)
└── Order/
    ├── 01 - Storefront - Checkout Desde Carrito.bru            # POST   /api/v1/store/{{tenantSlug}}/orders/checkout (201 Created)
    ├── 02 - Storefront - Listar Mis Pedidos en Tienda.bru      # GET    /api/v1/store/{{tenantSlug}}/orders (200 OK)
    ├── 03 - Storefront - Obtener Detalle de Mi Pedido.bru      # GET    /api/v1/store/{{tenantSlug}}/orders/{{orderId}} (200 OK)
    ├── 04 - Backoffice - Listar Pedidos con Filtros.bru        # GET    /api/v1/tenants/{{tenantId}}/orders (200 OK - Staff)
    ├── 05 - Backoffice - Pantalla de Cocina KDS.bru            # GET    /api/v1/tenants/{{tenantId}}/orders/kds (200 OK - Staff)
    ├── 06 - Backoffice - Mis Entregas Asignadas (Delivery).bru # GET    /api/v1/tenants/{{tenantId}}/orders/my-deliveries (200 OK)
    ├── 07 - Backoffice - Obtener Detalle de Pedido.bru         # GET    /api/v1/tenants/{{tenantId}}/orders/{{orderId}} (200 OK - Staff)
    ├── 08 - Backoffice - Cambiar Estado del Pedido.bru         # PATCH  /api/v1/tenants/{{tenantId}}/orders/{{orderId}}/status (200 OK - Staff)
    ├── 09 - Backoffice - Asignar Repartidor al Pedido.bru      # PATCH  /api/v1/tenants/{{tenantId}}/orders/{{orderId}}/assign (200 OK - OWNER, ADMIN, CASHIER)
    └── 10 - Storefront - Cancelar Mi Pedido en PENDIENTE con Reembolso.bru # POST /api/v1/store/{{tenantSlug}}/orders/{{orderId}}/cancel (200 OK)
├── Payment/
│   ├── 01 - OAuth - Obtener URL de Conexion MP (Solo OWNER).bru # GET    /api/v1/tenants/{{tenantId}}/mercadopago/connect (200 OK)
│   ├── 02 - OAuth - Consultar Estado de Conexion MP (OWNER o ADMIN).bru # GET /api/v1/tenants/{{tenantId}}/mercadopago/status (200 OK)
│   ├── 03 - OAuth - Callback de Autorizacion MP (Publico).bru  # GET    /api/v1/mercadopago/oauth/callback (200 OK / 302 Found)
│   ├── 04 - OAuth - Desvincular Cuenta MP (Solo OWNER).bru     # DELETE /api/v1/tenants/{{tenantId}}/mercadopago (200 OK)
│   ├── 05 - Storefront - Consultar Estado de Mi Pago.bru       # GET    /api/v1/store/{{tenantSlug}}/payments/{{paymentId}} (200 OK)
│   ├── 06 - Backoffice - Listar Pagos del Restaurante (OWNER o ADMIN).bru # GET /api/v1/tenants/{{tenantId}}/payments (200 OK)
│   ├── 07 - Backoffice - Obtener Pago de una Orden (Miembro).bru # GET /api/v1/tenants/{{tenantId}}/orders/{{orderId}}/payment (200 OK)
│   ├── 08 - Webhook - Notificacion de Orden Mercado Pago (Firma HMAC).bru # POST /api/v1/webhooks/mercadopago (200 OK)
│   └── 09 - Webhook - Notificacion de Suscripcion Preapproval (Firma HMAC).bru # POST /api/v1/webhooks/mercadopago (200 OK)
└── Tracking/
    ├── 01 - Publico - Estimar Cobertura, Tarifa y Tiempo de Entrega (ETA).bru # GET /api/v1/store/{{tenantSlug}}/delivery-estimate (200 OK - público)
    ├── 02 - Repartidor - Enviar Ping de Ubicacion GPS (EN_CAMINO).bru       # PUT /api/v1/tenants/{{tenantId}}/orders/{{orderId}}/tracking/location (200 OK)
    ├── 03 - Storefront Cliente - Consultar Seguimiento de Mi Pedido (Polling cada 15s).bru # GET /api/v1/store/{{tenantSlug}}/orders/{{orderId}}/tracking (200 OK)
    ├── 04 - Backoffice - Obtener Tracking y Recorrido de Orden (Miembro).bru # GET /api/v1/tenants/{{tenantId}}/orders/{{orderId}}/tracking (200 OK)
    └── 05 - Backoffice - Mapa de Repartidores Activos en Ruta (OWNER o ADMIN).bru # GET /api/v1/tenants/{{tenantId}}/tracking/active (200 OK)
```

---

## ⚡ Automatización de Variables y Tokens

* **Autenticación (`01 - Registro` / `02 - Login` / `07 - Mi Perfil`):** guardan automáticamente `accessToken`, `refreshToken` y `userId` en el entorno activo.
* **Direcciones (`10 - Crear Direccion`):** guarda automáticamente `addressId` para usarlo en actualización (`12`) y eliminación (`13`).
* **Suscripciones (`01 - Listar Planes` / `02 - Crear Plan` / `07 - Iniciar Trial`):** guardan automáticamente `planId` y `subscriptionId`.
* **Restaurantes (`01 - Crear Restaurante` / `02 - Obtener Mi Restaurante`):** guardan `tenantId` y `tenantSlug` (y resuelven la suscripción vigente automáticamente sin pedir `subscriptionId` en el body).
* **Equipo y Clientes (`08 - Agregar Miembro` / `11-12 - Store Auth`):** guardan `memberId` y `customerId` para las rutas de administración de staff y clientes.
* **Catálogo e Impuestos (`Catalog/01..18` y `Store/20..21`):** guardan automáticamente `taxId`, `categoryId`, `productId` y `priceId` en el entorno activo tanto al crear como al listar recursos.
* **Carrito de Compras (`Cart/01..02`):** guardan automáticamente `cartId` y `cartItemId` al consultar el carrito o agregar un producto.
* **Pedidos y Cocina (`Order/01..04`):** guardan automáticamente `orderId` al ejecutar el checkout o al listar pedidos.
* **Inventario y Recetas (`Inventory/01..17`):** guardan automáticamente `inventoryItemId`, `stockId` e `ingredientId` en el entorno activo.
* **Pagos (`Payment/01..09`):** guardan automáticamente `paymentId` y `mpOrderId` para consultar el estado del pago, reconciliación y webhooks.
* Cada petición incluye aserciones automáticas en la pestaña **Tests**.
