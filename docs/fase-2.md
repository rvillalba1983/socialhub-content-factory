# SocialHub — Fase 2: backend, persistencia y frontend React

Estado: **terminada** (2 nov 2026). Bloques 1 y 2 fusionados en `main`. Criterios comprobados más abajo.

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
| 8 | Acceso | ~~Contraseña local~~ → **sin contraseña** desde el 1 nov: solo `127.0.0.1`, comprobación de Host y cabecera `X-SocialHub-Request` |
| 9 | Creación de la contraseña | ~~Anulada el 1 nov~~ |
| 10 | Recuperación | ~~Anulada el 1 nov~~ |
| 11 | Dirección | `http://localhost:8686`, configurable. PostgreSQL usa otro puerto interno |

Pendiente: compilar en el entorno de Claude. Hoy tiene cortado internet salvo GitHub (Maven Central, npm y la descarga de Java 25 incluidos). Raúl lo desbloqueará desde el ordenador; hasta entonces no se escribe código.

## Decisiones de la revisión del 26 oct

| # | Tema | Decisión |
|---|---|---|
| 12 | Drive | Carpeta sincronizada `G:\` (Google Drive para escritorio); sin la API de Drive por ahora |
| 13 | Transición | Las tareas programadas actuales siguen igual hasta validar SocialHub |
| 14 | Fechas de los datos de ejemplo | Relativas al día actual |
| 15 | Movimiento | Simulador de tareas solo en modo demo, indicado claramente |
| 16 | Git | Rama y pull request por bloque; Actions compila y prueba; se fusiona a `main` con el OK de Raúl |

## Modelo de datos del incremento (Flyway `V1__core_model.sql`)

Rehecho tras la revisión de la PR #1 (ver `decisiones.md`, 1 y 2 de noviembre):

- `brand`, `promotion` (objetivos, normas y tareas de Raúl en columnas `text[]`) y `common_rule`.
- Colecciones de la promoción: `promotion_milestone`, `promotion_resource`, `promotion_automation`, `promotion_route` y `promotion_setting`.
- `station`: las 8 estaciones, con estado propio, independiente del de sus tareas.
- `task` (entradas en `text[]`), `task_step`, `task_check`, `task_dependency` y `task_event` (historial, solo se añade).
- `publication`. Una restricción impide que esté «publicada» sin confirmación.
- `armory_item`: agentes, herramientas, servicios y dispositivos en una sola tabla.
- `external_operation`: registro de operaciones externas.
- Auditoría de Hibernate Envers: `revinfo`, `task_aud`, `station_aud` y `publication_aud`.
- Los datos de ejemplo llevan `is_demo = true`.
- La contraseña y las sesiones se han quitado (decisión del 1 de noviembre).

## Máquinas de estados (validadas en el backend)

- **Tarea:** inactiva → en cola → en progreso → en espera | bloqueada | error | completada | cancelada.
  - Reintentar, reanudar y reactivar vuelven a «en cola».
  - Los reintentos tienen límite.
  - Una tarea bloqueada pasa a «en cola» automáticamente cuando se cumplen sus dependencias.
- **Publicación:** idea → propuesta → en producción → borrador → pendiente de revisión → aprobada → programada → publicando → publicada | error de publicación | cancelada.
  - No pasa a «publicada» sin confirmación externa.

## API

API first: `contracts/openapi.yaml` es la fuente de la verdad, según las guías REST de Zalando. La tabla de rutas está en `backend/README.md`.

## Entregables y criterios de aceptación

1. `mvn verify` en verde, con pruebas de:
   - Transiciones válidas e inválidas.
   - Dependencias.
   - Arranque y parada de PostgreSQL.
   - Migraciones.
2. SocialHub arranca en Windows:
   - Inicia PostgreSQL, aplica las migraciones y sirve la web en `localhost:8686`.
3. El anillo en React se ve como la propuesta moderna, con los datos de la base de datos marcados como ejemplo.
4. Las acciones cambian el estado en la base de datos y el anillo se actualiza solo.
5. Al reiniciar SocialHub, los estados se conservan.

### Comprobación (2 nov 2026)

1. ✅ `mvn verify` en verde en Linux, Windows y macOS (GitHub Actions).
2. ✅ Arranque real en Windows y macOS (GitHub Actions, prueba bajo demanda):
   - El jar con la web arranca PostgreSQL y aplica las migraciones.
   - Sirve la web y la API en `localhost:8686`.
   - Rechaza las órdenes sin la cabecera de protección y acepta las que la llevan.
   - Queda pendiente la prueba en el PC de Raúl cuando vuelva a tenerlo.
3. ✅ El anillo en React se ve como la propuesta moderna, con datos de ejemplo marcados como tales. Revisado con capturas.
4. ✅ Las órdenes cambian el estado en la base de datos y el anillo se recarga por SSE.
5. ✅ Al reiniciar se conservan los datos (`EmbeddedPostgresTest`).

