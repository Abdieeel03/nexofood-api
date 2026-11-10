# Guía de Contribución — NexoFood API

¡Gracias por tu interés en contribuir a **NexoFood API**! Esta guía describe las convenciones del proyecto, el flujo de trabajo y los criterios mínimos para mantener la calidad, el aislamiento multi-tenant y la documentación al día.

---

## 🛠️ Requisitos del Entorno

- **Java 21** (JDK 21)
- **Maven Wrapper** incluido en el repositorio (`./mvnw` en Linux/macOS o `.\mvnw.cmd` en Windows)
- **Docker** con PostgreSQL 16 + PostGIS (`postgis/postgis:16-3.4`) para pruebas locales de integración y arranque completo
- **Bruno** (opcional, para probar y mantener la colección de endpoints en `bruno/`)

---

## 🔀 Flujo de Trabajo con Git

1. Haz un **fork** (si contribuyes desde fuera) o crea una rama descriptiva desde **`dev`** (rama de integración activa del repositorio):
   - `feat/<nombre-corto>` o `feature/<nombre-corto>` para nuevas funcionalidades
   - `fix/<nombre-corto>` para corrección de errores
   - `docs/<nombre-corto>` para mejoras de documentación
   - `refactor/<nombre-corto>` para mejoras internas sin cambio de comportamiento
   - `test/<nombre-corto>` para incorporación o ajuste de pruebas
2. Mantén los commits pequeños, claros y enfocados usando **Conventional Commits** (`tipo(alcance): descripción`), por ejemplo:
   - `feat(tracking): add live ETA recalculation`
   - `fix(order): validate delivery radius before checkout`
   - `test(security): agregar pruebas de aislamiento RLS`
   - Alcances frecuentes: `identity`, `subscription`, `store`, `catalog`, `inventory`, `cart`, `order`, `tracking`, `payment`, `security`, `common`, `bruno`, `docs`.
3. Antes de abrir tu Pull Request hacia `dev`, asegúrate de que el proyecto compile y pasen todas las pruebas:

```bash
# Suite unitaria y de controladores (sin requerir PostgreSQL activo)
# Linux / macOS (Bash / Zsh):
./mvnw test -Dtest='!NexofoodApiApplicationTests'

# Windows (PowerShell):
.\mvnw.cmd test "-Dtest=!NexofoodApiApplicationTests"

# Suite completa (con PostgreSQL + PostGIS activo)
./mvnw test

# Recomendado si agregas o actualizas dependencias en pom.xml:
./mvnw dependency-check:check
```

---

## 🏗️ Convenciones de Arquitectura y Código

El proyecto sigue una arquitectura de **Monolito Modular (DDD / Hexagonal)** organizada por dominio bajo `src/main/java/lat/nexofood/api/modules/`:

```text
modules/<modulo>/
├── api/               # (Opcional) Contratos públicos compartidos entre módulos
├── domain/            # Entidades JPA, enums, value objects y reglas puras de dominio
├── infrastructure/    # Repositorios Spring Data y configuración técnica (espacial/RLS)
├── application/       # Casos de uso (usecase/), servicios (service/) y jobs programados
└── web/               # Controladores REST (controller/), DTOs (dto/request, dto/response) y mappers
```

### Reglas Obligatorias

1. **Aislamiento Multi-Tenant y RLS:**
   - Toda entidad de negocio perteneciente a un restaurante debe incluir `tenant_id` y filtrarse por `tenantId` en repositorios y servicios.
   - Si agregas nuevas tablas con `tenant_id`, actualiza [`script.sql`](script.sql) y habilita **Row-Level Security (`tenant_isolation`)** al inicializar (como en [`TrackingSpatialRlsInitializer`](src/main/java/lat/nexofood/api/modules/tracking/infrastructure/config/TrackingSpatialRlsInitializer.java)).
2. **Autorización, Validación y Respuestas:**
   - Protege los endpoints de backoffice mediante `@PreAuthorize` apoyado en [`TenantAccessService`](src/main/java/lat/nexofood/api/common/security/TenantAccessService.java) (`@tenantAccess.isMember(#tenantId)`, `@tenantAccess.hasAnyRole(...)`, `@tenantAccess.isOwner(#tenantId)`).
   - Define los DTOs como Java `record`, valídalos con **Jakarta Validation** (`@NotNull`, `@NotBlank`, `@DecimalMin`, etc.) y retorna el envoltorio estándar [`ApiResponse<T>`](src/main/java/lat/nexofood/api/common/response/ApiResponse.java).
   - Deja que [`GlobalExceptionHandler`](src/main/java/lat/nexofood/api/common/exception/GlobalExceptionHandler.java) centralice los errores en [`ErrorResponse`](src/main/java/lat/nexofood/api/common/response/ErrorResponse.java) sin exponer detalles internos en errores 401/403/500.
3. **Entidades JPA y Lombok:**
   - Usa `@Builder`, `@Getter`, `@Setter`, `@NoArgsConstructor` y `@AllArgsConstructor`; evita `@Data` en entidades JPA.
   - Usa `@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)` incluyendo solo el `@Id`, excluye relaciones en `@ToString` (`@ToString.Exclude`) y persiste enums con `@Enumerated(EnumType.STRING)`.
4. **Geoespacial (PostGIS):**
   - Utiliza el sistema de referencia **WGS84 (`SRID 4326`)** con `org.locationtech.jts.geom.Point` y valida coordenadas mediante [`GeoUtils`](src/main/java/lat/nexofood/api/common/util/GeoUtils.java).
5. **Auditoría y Manejo de Secretos:**
   - Registra acciones sensibles (cambios de rol, bloqueos, precios, cancelaciones, etc.) usando [`SecurityAuditLogger`](src/main/java/lat/nexofood/api/common/audit/SecurityAuditLogger.java).
   - Nunca incluyas secretos, tokens ni credenciales reales en el código o en commits. Usa variables de entorno y documenta cualquier nueva propiedad en [`.env.example`](.env.example).
6. **Pruebas, Colección Bruno y Documentación:**
   - Toda nueva funcionalidad o corrección debe incluir pruebas unitarias/MockMvc en `src/test/java`.
   - Si agregas o modificas un endpoint REST, actualiza también:
     - La colección de **Bruno** en [`bruno/`](bruno/README.md) (con aserciones en `tests {}`)
     - [`docs/API_DOCUMENTATION.md`](docs/API_DOCUMENTATION.md) siguiendo las convenciones de [`docs/DOCUMENTATION_GUIDE.md`](docs/DOCUMENTATION_GUIDE.md).

---

## 📋 Checklist antes de enviar un Pull Request

- [ ] La rama parte de `dev` actualizada y el Pull Request apunta a `dev`.
- [ ] El código compila y pasa `./mvnw test -Dtest='!NexofoodApiApplicationTests'` (o `.\mvnw.cmd test "-Dtest=!NexofoodApiApplicationTests"` en PowerShell).
- [ ] No se exponen secretos ni credenciales reales (variables documentadas en `.env.example` si aplica).
- [ ] Los nuevos endpoints cuentan con anotaciones `@Operation` (OpenAPI) y pruebas de autorización.
- [ ] Se actualizó la colección en `bruno/` y la documentación correspondiente en `docs/`.

---

## 🐛 Reporte de Errores y Seguridad

- **Bugs o mejoras:** abre un *Issue* describiendo los pasos para reproducir el problema, el comportamiento esperado, el obtenido y el entorno (Java, Docker/PostgreSQL, perfil activo).
- **Vulnerabilidades de seguridad:** no publiques credenciales, tokens ni exploits detallados en *Issues* públicos; repórtalos de forma privada a los mantenedores del repositorio.

---

## 📄 Licencia

Al contribuir a este repositorio, aceptas que tus aportes queden cubiertos bajo la [Licencia MIT](LICENSE).
