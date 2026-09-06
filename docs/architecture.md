# NEXOFOOD API - Documentación Técnica y Diseño de Arquitectura

## 1. Stack Tecnológico y Configuración Base

* **Lenguaje:** Java 21 (LTS)
* **Framework:** Spring Boot 4.x (Spring Framework 7, Jakarta EE 11)
* **Gestor de Dependencias:** Maven
* **Base de Datos:** PostgreSQL en Supabase (Extensiones: `uuid-ossp`, `postgis`)[cite: 1]
* **Persistencia:** Spring Data JPA / Hibernate 6+ / Hibernate Spatial[cite: 1]
* **Librería Espacial:** Java Topology Suite (`jts-core` 1.20+)[cite: 1]
* **Documentación API:** SpringDoc OpenAPI 3 (`springdoc-openapi-starter-webmvc-ui` 2.8.5)[cite: 1]
* **Boilerplate Reduction:** Project Lombok[cite: 1]
* **Identificadores del Proyecto:**
  * Group ID: `lat.nexofood`
  * Artifact ID: `nexofood-api`
  * Package Base: `lat.nexofood.api`

---

## 2. Arquitectura de Software y Estructura Modular

### 2.1. Patrón Monolito Modular (DDD / Hexagonal)
El backend desacopla la lógica en módulos independientes de dominio (*Bounded Contexts*) asistidos por una infraestructura común[cite: 1]:

```text
lat.nexofood.api
│
├── common/                  # Infraestructura transversal, excepciones y utilidades
│   ├── config/              # OpenApiConfig, JpaAuditingConfig, WebMvcConfig
│   ├── exception/           # GlobalExceptionHandler, CustomExceptions, ErrorResponse
│   ├── model/               # BaseEntity (UUID, created_at, updated_at)
│   └── util/                # GeoUtils, SecurityUtils
│
└── modules/                 # Módulos de dominio funcional
    ├── auth/                # Autenticación global SSO, JWT y emisión de Claims
    ├── subscription/        # Control de planes SaaS, membresías y validación de vigencia
    ├── tenant/              # Inquilinos/Restaurantes, miembros de staff (RBAC)
    ├── catalog/             # Categorías y Productos
    ├── cart/                # Gestión de carritos de compra e ítems
    ├── order/               # Órdenes, cálculo de distancias PostGIS y máquina de estados
    └── payment/             # Mercado Pago OAuth, Webhooks y auditoría de transacciones
```

### 2.2. Arquitectura Interna de un Módulo
Cada módulo funcional (`lat.nexofood.api.modules.<module>`) desacopla sus responsabilidades en las siguientes capas:

```text
module/
├── api/             # Contratos públicos Java (interfaces, DTOs y eventos compartidos entre módulos)
├── application/     # Casos de uso (UseCases), servicios de aplicación y coordinación transaccional
├── domain/          # Entidades, Enums, Value Objects y reglas de negocio puras
├── infrastructure/  # Implementaciones técnicas (Spring Data JPA, repositorios, clientes externos)
│   └── repository/
└── web/             # Capa HTTP externa (Controllers REST, DTOs Request/Response, Mappers)
    ├── dto/
    │   ├── request/
    │   └── response/
    └── mapper/
```

### 2.3. Estrategia Multitenancy
* **Esquema:** *Shared Database, Shared Schema* (Base de datos y esquema compartidos)[cite: 1].
* **Segregación:** Filtrado obligatorio por `tenant_id` en todos los repositorios y servicios para aislar completamente las operaciones de cada inquilino[cite: 1, 2].

---

## 3. Reglas de Negocio y Flujos Críticos

1. **Modelo SaaS y Relación 1 a 1 (1 Negocio por Usuario y por Suscripción):**
   * El servicio se monetiza mediante tarifa plana periódica.
   * Cada suscripción activa (`subscriptions`) habilita exactamente un restaurante (`tenants`).
   * **Restricción 1 a 1 (Owner $\leftrightarrow$ Tenant):** Un usuario (`users`) solo puede ser propietario y registrar un único (1) restaurante o negocio (`tenants`). La relación está modelada como 1 a 1 (`@OneToOne` con `unique = true` en `owner_id`), impidiendo que una misma cuenta posea múltiples tiendas. Los demás usuarios pueden integrarse a una tienda existente como colaboradores mediante roles de membresía (`tenant_members`).
2. **Cobro Directo en Mercado Pago (Cero Comisión por Pedido):**
   * El restaurante vincula su cuenta mediante Mercado Pago OAuth (`mp_access_token`)[cite: 1, 2].
   * El 100% de la venta de los comensales ingresa de forma directa e inmediata a la cuenta del restaurante (sin retención de split fee por pedido)[cite: 1].
3. **Pago Previo Obligatorio para Cocina (Anti-Fraude):**
   * Ninguna orden pasa a estado `EN_PREPARACION` ni se visualiza en la pantalla de cocina (KDS) mientras el Webhook de Mercado Pago no confirme `payment.status == 'APPROVED'`[cite: 1].
4. **Validación Geoespacial de Cobertura (PostGIS):**
   * Se evalúa la distancia esférica entre las coordenadas del local y la dirección de entrega del cliente[cite: 1, 2]. Si la distancia supera el radio configurado (`delivery_radius_km`), el pedido a domicilio es bloqueado[cite: 1, 2].
5. **Snapshots Históricos Inmutables:**
   * Las órdenes preservan una copia inmutable del nombre del producto, precio unitario y dirección textual al momento exacto de la compra para garantizar consistencia histórica[cite: 1, 2].

---

## 4. Convenciones de Código y Patrones de Diseño

### 4.1. Patrón Builder Obligatorio
* **Instanciación Fluida:** Es mandatorio el uso del **Patrón Builder** (mediante la anotación `@Builder` de Lombok) en entidades, DTOs y modelos de dominio.
* **Ventajas:** Evita el antipatrón de constructores telescópicos con múltiples argumentos, permite construir objetos de forma inmutable y legible, y facilita la inicialización de atributos por defecto usando `@Builder.Default`.

### 4.2. Mapeo y Persistencia con JPA
* **Tipos de Datos Espaciales:** Usar `org.locationtech.jts.geom.Point` mapeado con `@Column(columnDefinition = "geometry(Point, 4326)")`[cite: 1].
* **Campos de Texto Extenso:** Usar `@Column(columnDefinition = "TEXT")` para descripciones y direcciones.
* **Enums:** Persistir exclusivamente con `@Enumerated(EnumType.STRING)` (mapeo a `VARCHAR` para facilitar migraciones y compatibilidad con JPA).
* **Auditoría:** Extender de una clase abstracta `@MappedSuperclass` con anotaciones `@CreatedDate` y `@LastModifiedDate` (`OffsetDateTime`).

### 4.3. Uso de Lombok en Entidades y Servicios
* **Evitar `@Data`:** Reemplazar por `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor` y `@Builder`.
* **Identidad Controlada:** Anotar `@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)` incluyendo exclusivamente el atributo `@Id` con `@EqualsAndHashCode.Include`.
* **Seguridad en Logs:** Excluir relaciones bidireccionales o atributos pesados en `@ToString` (`@ToString.Exclude` o `@ToString(exclude = {...})`).
* **Transferencia de Datos:** Implementar Java `record` para todos los DTOs de entrada y salida (Requests/Responses).
