# NEXOFOOD API

Backend para la plataforma SaaS multitenant de pedidos y gestión gastronómica.

## Base de Datos (PostgreSQL + PostGIS)

El entorno local utiliza un contenedor Docker con PostgreSQL 16 y PostGIS habilitado.

### Datos de Conexión Local

| Parámetro | Valor por defecto | Variable de Entorno |
| :--- | :--- | :--- |
| **Host** | `localhost` | - |
| **Puerto** | `5432` | - |
| **Base de Datos** | `nexofood_db` | `DB_URL` (`jdbc:postgresql://localhost:5432/nexofood_db`) |
| **Usuario** | `postgres` | `DB_USERNAME` |
| **Contraseña** | `postgres` | `DB_PASSWORD` |

### Comandos para Gestionar el Contenedor

* **Iniciar contenedor existente:**
  ```bash
  docker start nexofood-postgres
  ```

* **Detener contenedor:**
  ```bash
  docker stop nexofood-postgres
  ```

* **Crear contenedor nuevamente (si fue eliminado):**
  ```bash
  docker run -d --name nexofood-postgres \
    -e POSTGRES_DB=nexofood_db \
    -e POSTGRES_USER=postgres \
    -e POSTGRES_PASSWORD=postgres \
    -p 5432:5432 \
    postgis/postgis:16-3.4
  ```

* **Verificar extensiones habilitadas (`uuid-ossp`, `postgis`):**
  ```bash
  docker exec -it nexofood-postgres psql -U postgres -d nexofood_db -c '\dx'
  ```

## Ejecución del Proyecto

Para compilar e iniciar la aplicación:

```bash
./mvnw clean spring-boot:run
```
