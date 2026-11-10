# NexoFood API

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%2B%20PostGIS-336791.svg)](https://postgis.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Backend REST multi-tenant para una plataforma SaaS de pedidos en línea, gestión de restaurantes, cocina (KDS), inventario y seguimiento geoespacial de entregas en tiempo real.

---

## ✨ Características Principales

- **Arquitectura SaaS Multi-Tenant con RLS:** Aislamiento estricto por `tenant_id` en la capa de aplicación (`TenantContext`) y en base de datos mediante **Row-Level Security (`FORCE ROW LEVEL SECURITY`)** en PostgreSQL.
- **Identidad, Seguridad y RBAC:**
  - Autenticación JWT (`access` + `refresh` tokens con rotación obligatoria, detección de reutilización e invalidación global por `tokenVersion`).
  - Cifrado **AES-256-GCM** para credenciales sensibles, bloqueo temporal tras intentos fallidos de inicio de sesión, política de contraseñas y *rate limiting* con **Bucket4j**.
  - Cabeceras de seguridad (`HSTS`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`), auditoría de acciones sensibles (`SecurityAuditLogger`) y roles por restaurante (`OWNER`, `ADMIN`, `COCINERO`, `REPARTIDOR`).
- **Suscripciones y Planes SaaS:** Gestión de planes, periodos de prueba (`trial`), verificación de límites y vigencia con `SubscriptionGuard`, y expiración programada.
- **Catálogo, Precios Dinámicos e Impuestos:** Categorías ordenables, productos, reglas de precios promocionales por prioridad/vigencia horaria e impuestos configurables con *snapshot* histórico inmutable por orden.
- **Inventario, Kardex y Recetas:** Control de insumos, existencias con bloqueo pesimista y alertas de stock mínimo, Kardex auditable (`ENTRY`, `EXIT`, `ADJUSTMENT`) y descuento automático de ingredientes al pasar pedidos a cocina.
- **Carrito, Checkout y KDS:** Carrito persistente por tienda con recálculo en vivo, checkout con validación de cobertura, máquina de estados (`PENDIENTE` → `EN_PREPARACION` → `LISTO_PARA_ENTREGA` → `EN_CAMINO` → `ENTREGADO` / `CANCELADO`) y cola FIFO de cocina.
- **Geoespacial (PostGIS) y Tracking en Tiempo Real:**
  - Validación de radio de cobertura (`deliveryRadiusKm`) y estimación pública de tarifa/ETA previa al checkout (`/api/v1/store/{slug}/delivery-estimate`).
  - Cálculo automático de ETA al asignar repartidor y recálculo dinámico en vivo con cada ping GPS del motorizado.
  - Endpoints optimizados para polling REST (cada 15 s) con *throttling*, detección de saltos GPS anómalos, historial de ruta y limpieza programada.
- **Pagos 100% Mercado Pago y Conciliación Automática:**
  - **Orders API v1 + Checkout Pro:** Cobro en línea con tarjetas, Yape y PagoEfectivo; creación diferida de pedidos tras confirmación por webhook.
  - **OAuth 2.0 Multi-Tenant:** Vinculación directa de la cuenta del restaurante con credenciales cifradas con AES-256-GCM; 100% de los ingresos van al restaurante.
  - **Suscripciones Recurrentes Preaprobadas (`/preapproval`):** Cobro automatizado de planes SaaS con cancelación efectiva al término del ciclo de facturación.
  - **Seguridad en Webhooks:** Validación criptográfica de firmas HMAC-SHA256 (`x-signature`) y reembolsos automáticos para cancelaciones en estado `PENDIENTE`.

---

## 🧱 Stack Tecnológico

- **Lenguaje / Framework:** Java 21 (LTS) · Spring Boot 4.1.1 (WebMVC, Data JPA, Validation, Security, Actuator)
- **Base de Datos y Geoespacial:** PostgreSQL 16 + PostGIS · Hibernate Spatial · JTS Core 1.20.0 (`SRID 4326`)
- **Seguridad y Calidad:** JJWT 0.12.7 · BCrypt · Bucket4j 8.14.0 · OWASP Dependency-Check 12.1.0
- **Productividad y Pruebas:** Project Lombok · JUnit 5 · Mockito · MockMvc
- **Documentación y Testing API:** Springdoc OpenAPI 2.8.5 (Swagger UI) · Colección Bruno (`123` requests)

---

## 📂 Estructura del Proyecto

```text
nexofood-api/
├── bruno/                           # Colección Bruno (123 requests en 9 módulos + entornos)
├── docs/                            # Documentación técnica, API, guía de estilo y roadmap
├── script.sql                       # Esquema SQL completo (PostgreSQL + PostGIS + triggers + RLS)
├── script_inserciones.sql           # Datos de prueba multi-tenant
├── GUIA_PRUEBAS_BD.md               # Guía de verificación de RLS, triggers y restricciones en pgAdmin
└── src/main/java/lat/nexofood/api/
    ├── common/                      # Excepciones globales, auditoría, seguridad RLS, cifrado y GeoUtils
    ├── security/                    # JWT filter/service, SecurityConfig, CORS y rate limiting
    └── modules/
        ├── identity/                # Usuarios, autenticación, direcciones GPS y admin global
        ├── subscription/            # Planes SaaS, suscripciones preaprobadas y expiración automática
        ├── store/                   # Tenants (restaurantes), staff, clientes y storefront público
        ├── catalog/                 # Impuestos, categorías, productos y reglas de precios
        ├── inventory/               # Insumos, stocks, Kardex/movimientos y recetas
        ├── cart/                    # Carrito de compras por tienda
        ├── order/                   # Órdenes, checkout, KDS y máquina de estados
        ├── tracking/                # Rastreo GPS del repartidor, ETA configurable y PostGIS/RLS
        └── payment/                 # Pasarela Mercado Pago Orders API, Checkout Pro, OAuth y webhooks
```

---

## 🚀 Puesta en Marcha Local

### Prerrequisitos

- **Java 21 (JDK 21)**
- **Docker** (o una instancia local de **PostgreSQL 16 + PostGIS**)
- **Maven Wrapper** incluido en el repositorio (`./mvnw` en Linux/macOS o `.\mvnw.cmd` en Windows)

### 1. Base de Datos (PostgreSQL + PostGIS en Docker)

| Parámetro | Valor por defecto | Variable de Entorno |
| :--- | :--- | :--- |
| **Host** | `localhost` | - |
| **Puerto** | `5432` | - |
| **Base de Datos** | `nexofood_db` | `DB_URL` (`jdbc:postgresql://localhost:5432/nexofood_db`) |
| **Usuario** | `postgres` | `DB_USERNAME` |
| **Contraseña** | `postgres` | `DB_PASSWORD` |

```bash
# Crear e iniciar el contenedor por primera vez
docker run -d --name nexofood-postgres \
  -e POSTGRES_DB=nexofood_db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgis/postgis:16-3.4

# Iniciar o detener en usos posteriores
docker start nexofood-postgres
docker stop nexofood-postgres

# Verificar extensiones habilitadas (postgis, uuid-ossp, etc.)
docker exec -it nexofood-postgres psql -U postgres -d nexofood_db -c '\dx'
```

> [!NOTE]
> En el perfil `dev`, Hibernate (`ddl-auto: update`) sincroniza las tablas al iniciar y `TrackingSpatialRlsInitializer` configura los índices GIST y políticas RLS de tracking. Si deseas inicializar la base de datos completa con triggers, columnas generadas y todas las políticas RLS (obligatorio para `prod`, que usa `ddl-auto: validate`), ejecuta [`script.sql`](script.sql) y opcionalmente [`script_inserciones.sql`](script_inserciones.sql).

### 2. Variables de Entorno y Perfiles

Consulta [`.env.example`](.env.example) como plantilla de variables. Ten en cuenta que Spring Boot **no carga automáticamente archivos `.env`**, por lo que debes exportarlas en tu terminal, IDE o contenedor si deseas personalizarlas.

- **Perfil `dev` (por defecto):** definido en [`application-dev.yaml`](src/main/resources/application-dev.yaml); incluye valores por defecto listos para desarrollo local (`CRYPTO_MASTER_KEY`, `JWT_SECRET`, `JWT_SECRET_REFRESH`), SQL logs y Swagger UI habilitado.
- **Perfil `prod`:** definido en [`application-prod.yaml`](src/main/resources/application-prod.yaml); usa `ddl-auto: validate`, deshabilita Swagger UI y exige definir explícitamente `CRYPTO_MASTER_KEY`, `JWT_SECRET`, `JWT_SECRET_REFRESH` y `CORS_ALLOWED_ORIGINS`.
- **Parámetros adicionales:** los límites de *rate limiting* y la configuración de tracking/ETA (`TRACKING_AVG_SPEED_KMH`, `TRACKING_ROAD_FACTOR`, `TRACKING_POLL_INTERVAL_SECONDS`, etc.) pueden ajustarse mediante variables de entorno definidas en [`application.yaml`](src/main/resources/application.yaml).

### 3. Compilar y Ejecutar

```bash
# Linux / macOS
./mvnw clean spring-boot:run

# Windows (PowerShell / CMD)
.\mvnw.cmd clean spring-boot:run
```

Una vez iniciada la aplicación en `http://localhost:8080`:

- **Base URL API:** `http://localhost:8080/api/v1`
- **Swagger UI (solo perfil `dev`):** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON (solo perfil `dev`):** `http://localhost:8080/v3/api-docs`
- **Health Check (Actuator):** `http://localhost:8080/actuator/health`

---

## 🧪 Pruebas y Auditoría de Seguridad

```bash
# Ejecutar pruebas unitarias y de controladores (sin requerir PostgreSQL activo)
# Linux / macOS (Bash / Zsh):
./mvnw test -Dtest='!NexofoodApiApplicationTests'

# Windows (PowerShell):
.\mvnw.cmd test "-Dtest=!NexofoodApiApplicationTests"

# Ejecutar la suite completa (requiere PostgreSQL + PostGIS corriendo)
./mvnw test

# Ejecutar escaneo de vulnerabilidades en dependencias (OWASP Dependency-Check)
./mvnw dependency-check:check
```

---

## 📚 Documentación y Colección Bruno

- **Documentación de endpoints para frontend:** [`docs/API_DOCUMENTATION.md`](docs/API_DOCUMENTATION.md)
- **Arquitectura del sistema:** [`docs/architecture.md`](docs/architecture.md)
- **Roadmap de implementación:** [`docs/ROADMAP.md`](docs/ROADMAP.md)
- **Guía de pruebas en PostgreSQL / pgAdmin:** [`GUIA_PRUEBAS_BD.md`](GUIA_PRUEBAS_BD.md)
- **Colección Bruno:** [`bruno/README.md`](bruno/README.md) con **123 requests** listos para ejecutar (`Identity`, `Subscription`, `Store`, `Catalog`, `Inventory`, `Cart`, `Order`, `Payment` y `Tracking`) y entornos `Local` / `Production`.

---

## 🤝 Contribuir

Las contribuciones son bienvenidas. Por favor revisa la [Guía de Contribución](CONTRIBUTING.md) antes de abrir un Pull Request.

---

## 📄 Licencia

Este proyecto está distribuido bajo la licencia **MIT**. Consulta el archivo [LICENSE](LICENSE) para más detalles.
