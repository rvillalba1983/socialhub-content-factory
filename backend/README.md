# backend

SocialHub en Spring Boot 4.1.2 con Java 25 LTS y Maven. Es un monolito modular con arquitectura hexagonal y DDD. Spring Modulith y jMolecules lo verifican en las pruebas.

## Módulos

| Módulo | Qué hace |
|---|---|
| `api` | Interfaces de los controladores y modelos, **generados** desde `contracts/openapi.yaml` (API first) |
| `platform` | Carpeta de datos, PostgreSQL incluido, errores de la API, seguridad local, avisos en directo (SSE) y `ExternalCalls` |
| `operations` | Registro de las operaciones externas, visible en `/api/external-operations` |
| `production` | Estaciones y tareas, con su máquina de estados |
| `publishing` | Publicaciones y su ciclo de vida. Nunca pasan a «publicada» sin confirmación |
| `promotion` | Marcas, promociones y reglas comunes |
| `armory` | Agentes, herramientas locales, servicios y dispositivos |
| `factory` | Vista completa del anillo (`/api/factory`) |
| `demo` | Datos de ejemplo marcados como tales y simulador (solo en modo demo) |

Cada módulo de negocio sigue la misma estructura:

```
production/
├── domain/                      Agregados (Task, Station), objetos valor y puertos. Sin Spring ni JPA
├── ProductionService.java       Casos de uso (puerto primario)
├── ProductionEvents.java        Eventos de dominio que escuchan otros módulos
└── adapter/
    ├── in/web/                  Controladores que implementan las interfaces generadas + MapStruct
    └── out/persistence/         Entidades JPA, Spring Data, Envers + MapStruct
```

## Librerías

- **Spring Data JPA.** Los filtros dinámicos usan Specifications.
- **Hibernate Envers.** Auditoría en las tablas `*_aud`.
- **openapi-generator.** Genera interfaces y modelos (`*Dto`) en `target/generated-sources`.
- **MapStruct.** Traduce entre API, dominio y JPA.
- **Lombok.** Constructores, getters y builders.
- **jMolecules + Spring Modulith + ArchUnit.** Comprueban la arquitectura.
- **Micrometer Observation + tracing.** Métricas e ID de traza de cada operación externa. No se exporta a ningún servidor.

## Ejecutar

```bash
mvn spring-boot:run
```

Se abre en http://localhost:8686. Al arrancar:

1. Descomprime PostgreSQL 18.6 incluido. Los binarios de desarrollo vienen de `io.zonky.test.postgres`; el instalador traerá los suyos.
2. Crea la base de datos en la carpeta de datos y la arranca con `pg_ctl`, escuchando solo en `127.0.0.1`.
3. Aplica las migraciones de Flyway. Hibernate solo valida que el esquema coincide.
4. Si la base de datos del modo demo está vacía, carga en ella los datos de ejemplo.

## Modos real y demo

- Cada modo tiene su propia base de datos en el mismo PostgreSQL incluido: `socialhub` (real) y `socialhub_demo` (demo). Nunca se mezclan.
- Flyway migra las dos al arrancar.
- El modo se elige desde la cabecera de la app (`PUT /api/app-mode`) y se recuerda en el archivo `mode` de la carpeta de datos.
- La primera vez se usa `socialhub.default-mode`, que por defecto es `REAL`.
- En modo real solo existen las 8 estaciones (migración `V2`). Las marcas y las promociones se dan de alta desde la app.
- El simulador solo mueve las tareas en modo demo.

Al parar SocialHub, también se para PostgreSQL.

Carpeta de datos por defecto:

- Windows: `%LOCALAPPDATA%\SocialHub`
- macOS: `~/Library/Application Support/SocialHub`
- Linux: `~/.local/share/socialhub`

Se puede cambiar con `--socialhub.data-dir=…`. Dentro están `pgdata/` (la base de datos), `logs/` y `runtime/` (los binarios).

Opciones útiles:

- `--socialhub.postgres.port=0`: elige un puerto libre para PostgreSQL.
- `--socialhub.demo.simulate=false`: las tareas de ejemplo no avanzan solas.

## Seguridad

La app es para un ordenador personal, así que no tiene contraseña. A cambio:

- Solo escucha en `127.0.0.1`.
- Solo acepta peticiones dirigidas a `localhost` o `127.0.0.1`, para protegerse del *DNS rebinding*.
- Las peticiones que cambian algo necesitan la cabecera `X-SocialHub-Request: 1`. Otras webs abiertas en el navegador no pueden añadirla.

## Observabilidad

- Toda operación externa pasa por `ExternalCalls`. Cada llamada deja:
  - La métrica `socialhub.external.call` en `/actuator/metrics`.
  - El ID de traza en los registros: `[traceId,spanId]`.
  - Una fila en `external_operation`.
- Los fallos también se escriben en el registro como `WARN`.
- Las operaciones se guardan 30 días. Se consultan en `GET /api/external-operations?system=postgres&outcome=FAILURE`.

## Pruebas

```bash
mvn verify
```

Incluyen:

- La máquina de estados de tareas, estaciones y publicaciones.
- El arranque, la parada y el reinicio de PostgreSQL sin perder datos.
- La API completa contra una base de datos real:
  - Seguridad local.
  - Órdenes permitidas y rechazadas.
  - Dependencias y pausa de estaciones.
  - Filtros y paginación.
  - Auditoría de Envers y operaciones externas.
  - Que la vista del anillo no hace más de 20 consultas.
- Los límites entre módulos (Spring Modulith) y las reglas de DDD y arquitectura hexagonal (jMolecules).

En Linux y macOS no se pueden ejecutar como `root`, porque `initdb` se niega a funcionar con ese usuario.

En Linux, el FFmpeg incluido necesita algunas bibliotecas del sistema: `libxcb1 libxcb-shm0 libasound2t64 libpulse0 libva2 libva-drm2`. En Windows y macOS no hace falta instalar nada.

GitHub Actions las ejecuta en Linux en cada cambio. Windows y macOS solo se prueban bajo demanda, desde la pestaña Actions con la opción «Probar también en Windows y macOS».

## API

El contrato completo está en `contracts/openapi.yaml`. Sigue las guías REST de Zalando:

- JSON en snake_case.
- Valores fijos en MAYÚSCULAS.
- Errores en `application/problem+json`.
- Paginación con `offset` y `limit`.

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/factory` | Todo el anillo: estaciones, tareas, promociones, publicaciones, armería y avisos |
| GET · PUT | `/api/app-mode` | Modo real o demo: `{"mode":"DEMO"}` |
| GET · POST | `/api/brands` · PUT `/api/brands/{brand_id}` | Marcas: alta y edición |
| POST | `/api/promotions` · PUT `/api/promotions/{promotion_id}` | Promociones: alta y edición |
| GET | `/api/promotions` · `/api/promotions/{promotion_id}` | Promociones |
| GET | `/api/promotions/{promotion_id}/publications` | Calendario, con filtros `status`, `platform`, `from`, `to`, `flagged` y paginación |
| GET | `/api/tasks` · `/api/tasks/{task_id}` | Tareas, con filtros `station_id`, `promotion_id`, `status` y `human` |
| POST | `/api/tasks/{task_id}/commands` | Orden sobre una tarea: `{"type":"PAUSE"}`, `{"type":"CHANGE_AGENT","model":"OPUS"}`… |
| GET | `/api/stations` | Estaciones con sus contadores |
| POST | `/api/stations/{station_id}/commands` | `{"type":"PAUSE"}` o `{"type":"RESUME"}` |
| GET | `/api/armory-items` | Armería |
| GET | `/api/external-operations` | Registro de operaciones externas, con filtros `system` y `outcome` |
| GET | `/api/events` | Avisos en directo (SSE) |

Respuestas de error:

- Una orden no permitida en el estado actual responde 409.
- Un parámetro fuera de los límites del contrato responde 400.
