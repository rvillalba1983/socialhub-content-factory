# backend

SocialHub en Spring Boot 4.1.2 con Java 25 LTS y Maven. Es un monolito modular (Spring Modulith) con un paquete por dominio:

| Módulo | Qué hace |
|---|---|
| `platform` | Carpeta de datos, PostgreSQL incluido, errores de la API, reloj y avisos en directo (SSE) |
| `auth` | Contraseña local: se crea en el primer arranque, sesión de 30 días y reinicio con el archivo `reset-password` |
| `audit` | Auditoría de negocio |
| `brand` | Marcas y promociones (lecturas para el centro de control) |
| `production` | Estaciones y tareas, con su máquina de estados (`domain/Task`) |
| `publishing` | Publicaciones, con sus transiciones. Nunca pasan a «publicada» sin confirmación |
| `armory` | Agentes, herramientas locales y servicios |
| `controlcenter` | API de lectura del anillo (`/api/v1/factory`) y de cada promoción |
| `demo` | Datos de ejemplo marcados como tales y simulador (solo en modo demo) |

## Ejecutar

```bash
mvn spring-boot:run
```

Se abre en http://localhost:8686. Al arrancar:

1. Descomprime PostgreSQL 18.6 incluido. Los binarios de desarrollo vienen de `io.zonky.test.postgres`; el instalador traerá los suyos.
2. Crea la base de datos en la carpeta de datos y la arranca con `pg_ctl`, escuchando solo en `127.0.0.1`.
3. Aplica las migraciones Flyway.
4. Si la base de datos está vacía, carga los datos de ejemplo.

Al parar SocialHub, también se para PostgreSQL.

Carpeta de datos por defecto:
- Windows: `%LOCALAPPDATA%\SocialHub`
- macOS: `~/Library/Application Support/SocialHub`
- Linux: `~/.local/share/socialhub`

Se puede cambiar con `--socialhub.data-dir=…`. Allí están `pgdata/` (la base de datos), `logs/` y `runtime/` (los binarios).

Opciones útiles:
- `--socialhub.postgres.port=0`: elige un puerto libre para PostgreSQL.
- `--socialhub.demo.simulate=false`: las tareas de ejemplo no avanzan solas.

## Pruebas

```bash
mvn verify
```

Incluyen:
- La máquina de estados de tareas y publicaciones.
- El arranque, la parada y el reinicio de PostgreSQL sin perder datos.
- La API completa contra una base de datos real: acceso, acciones permitidas y rechazadas, dependencias, pausa de estaciones y reinicio de contraseña.
- La verificación de los límites entre módulos.

En Linux y macOS no se pueden ejecutar como `root`, porque `initdb` se niega a funcionar con ese usuario.

GitHub Actions las ejecuta en Linux en cada cambio. Windows y macOS solo se prueban bajo demanda, desde la pestaña Actions con la opción «Probar también en Windows y macOS».

## API (v1)

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/v1/auth/status` | ¿Hay que crear la contraseña? ¿Hay sesión? |
| POST | `/api/v1/auth/setup` · `/login` · `/logout` | Acceso. Cuerpo `{"password": "…"}` |
| GET | `/api/v1/factory` | Todo el anillo: estaciones, tareas, promociones, publicaciones, armería y avisos |
| GET | `/api/v1/promotions/{id}` | Centro de control de una promoción |
| POST | `/api/v1/tasks/{id}/actions/{acción}` | `pause`, `resume`, `cancel`, `retry`, `manual`, `approve`, `agent` (cuerpo `{"model":"Opus"}`)… |
| POST | `/api/v1/stations/{id}/actions/pause` · `/resume` | Pausar o reanudar una estación |
| GET | `/api/v1/events` | Avisos en directo (SSE) |
| GET | `/api/v1/audit` | Últimos eventos de auditoría |

Reglas de las peticiones:
- Las que cambian algo necesitan la cabecera `X-SocialHub-Request: 1`. Otras webs abiertas en el navegador no pueden añadirla.
- Una acción no permitida en el estado actual responde 409.
