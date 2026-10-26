# SocialHub — Fase 2: backend, persistencia y frontend React

Estado: **plan aprobado por respuestas de Raúl** · 26 oct 2026

## Decisiones (respuestas de Raúl, una a una)

| # | Tema | Decisión |
|---|---|---|
| 1 | Estructura del backend | Un único proyecto Maven con un paquete por dominio (`domain` / `application` / `adapters`). Spring Modulith verifica que los dominios no se mezclen |
| 2 | Alcance del incremento | Marcas, promociones, estaciones, tareas (con eventos y dependencias), publicaciones y auditoría; **además**, herramientas locales y agentes de IA para la armería |
| 3 | Datos iniciales | Solo los datos de ejemplo del prototipo, marcados como demostración |
| 4 | Frontend | La propuesta moderna se rehace ya en React + TypeScript + Vite y lee de la API |
| 5 | PostgreSQL | Gestor propio: SocialHub arranca y para PostgreSQL con `pg_ctl`, como en la prueba validada. Los binarios vienen con el instalador; en desarrollo y CI se descargan automáticamente según el sistema |
| 6 | Carpeta de datos | `%LOCALAPPDATA%\SocialHub` (Windows) y `~/Library/Application Support/SocialHub` (macOS). Configurable |
| 7 | Tiempo real | Server-Sent Events: el anillo se actualiza solo |
| 8 | Acceso | Contraseña local, aunque solo se escuche en `127.0.0.1` |
| 9 | Creación de la contraseña | Se crea al abrir la app la primera vez. Se guarda como hash y la sesión dura 30 días en ese navegador |
| 10 | Recuperación | Crear un archivo vacío `reset-password` en la carpeta de datos y reiniciar |
| 11 | Dirección | `http://localhost:8686`, configurable. PostgreSQL usa otro puerto interno |

Pendiente: dónde se compila. La decisión es desbloquear Maven Central y la descarga de Java 25 en el entorno de Claude. Raúl lo hará desde el ordenador.

## Modelo de datos del incremento (Flyway `V1__…`)

- `brand`, `brand_configuration`
- `promotion`, `promotion_goal`, `promotion_policy`, `promotion_milestone`
- `station`: las 8 estaciones, con estado propio, independiente del de sus tareas
- `task`, `task_dependency`, `task_event`, `task_step`, `task_check`
- `publication`, `publication_attempt`
- `local_tool`, `local_tool_capability`
- `ai_provider`, `ai_model`, `ai_agent`
- `audit_event`
- `app_user_credential`: el hash de la contraseña local, `app_session`
- `demo_marker` o columna `is_demo`: todo dato de demostración queda identificado

Los datos de ejemplo se cargan en una ubicación Flyway aparte (`db/demo`), que solo se activa con el perfil `demo`.

## Máquinas de estados (validadas en el backend)

- **Tarea:** inactiva → en cola → en progreso → en espera | bloqueada | error | completada | cancelada.
  - Reintentar, reanudar y reactivar vuelven a «en cola».
  - Los reintentos tienen límite.
  - Una tarea bloqueada pasa a «en cola» automáticamente cuando se cumplen sus dependencias.
- **Publicación:** idea → propuesta → en producción → borrador → pendiente de revisión → aprobada → programada → publicando → publicada | error de publicación | cancelada.
  - No pasa a «publicada» sin confirmación externa.

## API (v1, JSON)

- `GET /api/v1/factory`: estado del anillo (estaciones, contadores, avisos).
- `GET /api/v1/stations/{id}`, `GET /api/v1/tasks/{id}`, `POST /api/v1/tasks/{id}/actions/{action}`. Solo se aceptan las acciones válidas para el estado actual.
- `GET /api/v1/promotions/{id}` con calendario, publicaciones, recursos, automatización y configuración.
- `GET /api/v1/tools`, `GET /api/v1/agents`.
- `GET /api/v1/events`: flujo SSE de cambios.
- `POST /api/v1/auth/setup`, `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`.

## Entregables y criterios de aceptación

1. `mvn verify` en verde, con pruebas de:
   - Transiciones válidas e inválidas.
   - Dependencias.
   - Arranque y parada de PostgreSQL.
   - Migraciones.
2. SocialHub arranca en Windows:
   - Inicia PostgreSQL, aplica las migraciones y sirve la web en `localhost:8686`.
   - Pide crear la contraseña.
3. El anillo en React se ve como la propuesta moderna, con los datos de la base de datos marcados como ejemplo.
4. Las acciones cambian el estado en la base de datos y el anillo se actualiza solo.
5. Al reiniciar SocialHub, los estados se conservan.
