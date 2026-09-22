# Colección Bruno - NexoFood API

Esta carpeta contiene la colección de peticiones y pruebas automatizadas de **NexoFood API** lista para usar con [Bruno](https://www.usebruno.com/).

---

## 🚀 Cómo Abrir la Colección en Bruno

1. Abre la aplicación de escritorio **Bruno**.
2. En la pantalla inicial, haz clic en **"Open Collection"**.
3. Selecciona la carpeta `bruno` de este repositorio:
   ```text
   /home/abdieeel/proyectos/nexofood-api/bruno
   ```
4. En la esquina superior derecha, selecciona el entorno **`Local`** (o `Production`).

---

## 📂 Estructura de la Colección

```text
bruno/
├── bruno.json                               # Configuración de la colección
├── environments/
│   ├── Local.bru                            # baseUrl = http://localhost:8080
│   └── Production.bru                       # baseUrl = https://api.nexofood.lat
└── Identity/
    ├── 01 - Registro de Usuario.bru         # POST /api/v1/auth/register (201 Created)
    ├── 02 - Inicio de Sesion (Login).bru    # POST /api/v1/auth/login (200 OK)
    ├── 03 - Renovar Token (Refresh).bru     # POST /api/v1/auth/refresh (200 OK)
    ├── 04 - Error Registro - Email Invalido (400).bru
    ├── 05 - Error Registro - Email Duplicado (409).bru
    └── 06 - Error Login - Password Incorrecto (401).bru
```

---

## ⚡ Automatización de Tokens

* Al ejecutar **01 - Registro** o **02 - Login**, un script post-respuesta (`script:post-response`) guarda automáticamente los valores de `accessToken` y `refreshToken` en las variables de tu entorno activo.
* La petición **03 - Renovar Token (Refresh)** lee automáticamente `{{refreshToken}}` y guarda los nuevos tokens rotados.
* Cada petición incluye aserciones automáticas en la pestaña **Tests** (`expect(res.getStatus()).to.equal(...)`).
