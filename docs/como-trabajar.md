# Cómo trabajamos en SocialHub

Para Claude (y para Raúl, si quiere saber cómo va). Las reglas cortas están en `CLAUDE.md`. Aquí está el detalle.

## Una fase por conversación

Cada fase (o bloque grande) del proyecto se hace en una conversación nueva. Así el contexto es pequeño y barato, y nada depende de lo que se habló en otra conversación: todo lo necesario está en el repositorio.

**Al empezar una conversación**
1. Lee `docs/estado.md`: dónde estamos y qué sigue.
2. Lee el `docs/fase-N.md` de la fase que toca. Si no existe, la fase empieza con preguntas.
3. De `docs/decisiones.md` y `docs/especificacion.md`, busca solo lo que necesites; no los leas enteros.
4. Prepara el entorno si hace falta: `bash scripts/entorno/preparar.sh` (como root).
5. Si la fase es nueva: preguntas a Raúl, de una en una, con la opción recomendada primero. Después escribe el plan en `docs/fase-N.md` y pide el OK.

**Durante la fase**
- Una rama y una PR por bloque. Commit y push tras cada paso que funcione.
- Cada decisión nueva va a `docs/decisiones.md`, indicando si la tomó Raúl o fue técnica.

**Al cerrar la fase**
1. Todas las PR de la fase pasan en GitHub (Linux y Windows) y Raúl las ha mergeado.
2. Actualiza `docs/estado.md`, `docs/fase-N.md`, `docs/decisiones.md` y, si cambia algo que Raúl usa, `MANUAL.md`.
3. Copia al proyecto de Claude los documentos que hayan cambiado: `claude/estado.md`, `claude/decisiones.md`, `claude/fase-N.md`, `claude/manual.md` y `claude/como-trabajar.md` (`project_write` con `local_path`).
4. Dile a Raúl qué queda para la siguiente conversación.

## Quién hace cada cosa (modelo y esfuerzo)

La conversación con Raúl (preguntas, decisiones, plan y revisión) se queda en la sesión principal. Lo demás se delega en los subagentes de `.claude/agents/`, que ya llevan su modelo y su esfuerzo:

| Subagente | Modelo · esfuerzo | Para qué |
|---|---|---|
| `vigilante-ci` | Haiku · bajo | Esperar a GitHub Actions y resumir qué pasó y qué falló (relanza una vez los fallos del entorno) |
| `explorador` | Haiku · bajo | Buscar en el código y en la documentación sin cargar archivos enteros |
| `documentador` | Sonnet · bajo | Actualizar estado, fase, decisiones y manual a partir de hechos ya decididos |
| `probador-visual` | Sonnet · bajo | Arrancar la app, hacer capturas con Playwright y mirar errores de consola |
| `programador` | Sonnet · medio | Programar un paso con el plan ya aprobado, probarlo y subirlo a la rama |
| `revisor` | Opus · alto | Revisión final de la rama antes de pedir el OK a Raúl |

Reglas:
- **No se delega:** el diseño, las preguntas a Raúl, la depuración de algo que no se entiende ni el merge. El merge siempre lo hace Raúl.
- **Instrucciones completas:** al delegar, di la rama, el plan, los archivos y qué hay que devolver. El subagente no ha visto la conversación.
- **Si un subagente falla dos veces en lo mismo,** repite con el nivel siguiente (Haiku → Sonnet → Opus) o hazlo en la sesión.
- **Esperas largas** (CI, instaladores): `vigilante-ci` en segundo plano. Mientras tanto, la sesión sigue con otra cosa o termina el turno.
- Criterio general: skill `eleccion-modelo-esfuerzo`.

## Lo que hace GitHub (y ahorra trabajo a Claude)

| Workflow | Cuándo | Qué hace |
|---|---|---|
| `backend.yml` | Cada PR con cambios en `backend/` | Pruebas en Linux y Windows; arranque real del jar. Si falla una prueba, anota la prueba y su error (se leen con `scripts/estado-pr.sh`) |
| `frontend.yml` | Cada PR con cambios en `frontend/` o `contracts/` | Tipos de la API frente al contrato, pruebas y compilación |
| `mapa-codigo.yml` | Cada PR | Mapa 3D del código, enlazado en un comentario de la PR |
| `diagramas.yml` | PR que cambia un `.d2` | Genera los SVG con D2 y los sube a la rama: no hace falta instalar D2 |
| `instaladores.yml` | A mano, o PR que lo cambia | `.msi` (y `.dmg` si se pide), instalado y probado en Windows |

- **Batería completa de pruebas:** la pasa GitHub. En local, solo las pruebas afectadas.
- **Resultado de una PR:** `scripts/estado-pr.sh <PR>` espera y resume en pocas líneas, o el subagente `vigilante-ci`.
- **macOS:** solo a mano (`otros_sistemas=windows-y-macos`), cuando haya una versión utilizable. Gasta 10 veces más minutos.

## Entorno de Claude en la nube

Se pierde al reiniciar el contenedor; `scripts/entorno/preparar.sh` lo deja listo.

- Java 25 en `/opt/jdk` (`/opt/jdk/env.sh`) y Maven en `/opt/maven`.
- `/opt/jdk/mvnu`: Maven como usuario `claude`, con el proxy de la sesión. Las pruebas que arrancan PostgreSQL no pueden ir como root. Antes de compilar: `chown -R claude .`.
- `/opt/jdk/npmp`: npm con el proxy de la sesión.
- **Pruebas del backend:** `bash scripts/entorno/pruebas.sh 'ClaseTest,OtraTest'`. Imprime el resumen; el registro completo queda en `/tmp/socialhub-pruebas.txt`.
- **Frontal:** en `frontend/`, `npm test` y `npm run build`. Nunca `npx tsc -b`, que deja `.js` dentro de `src/`.
- **Tipos de la API:** `cd frontend/tools/openapi && /opt/jdk/npmp run generate`.
- **Arranque real:**
  1. Compila la web (`frontend`: `npm run build`).
  2. Empaqueta: `cd backend && /opt/jdk/mvnu -q -DskipTests package`.
  3. Arranca como `claude`, con una carpeta de datos en `/tmp`:
     `su claude -s /bin/bash -c "nohup /opt/jdk/jdk-25*/bin/java -jar target/socialhub.jar --socialhub.data-dir=/tmp/sh/data --socialhub.postgres.runtime-dir=$PWD/target/pg-runtime > /tmp/sh/app.log 2>&1 &"`
  4. Para hablar con la app: `curl --noproxy '*' http://127.0.0.1:8686/...`.
- **Playwright** está instalado (`require('playwright')`, `chromium.launch()` sin opciones).
- **GitHub:** `gh` con la API REST, no GraphQL. Nunca force push ni merge.

## SocialHub en el PC de Raúl

- Instalado con el `.msi` (en Archivos de programa). Se abre con su acceso directo, en `http://localhost:8686`, y se cierra con el botón «Cerrar».
- Datos, copias y registro en `%LOCALAPPDATA%\SocialHub`. El registro está en `logs\socialhub.log` y el de PostgreSQL en `logs\pg_ctl.log`. Si algo falla, pídele esos dos archivos.
- Instalador nuevo: Actions › Instaladores (versión de tres números). Se instala encima del anterior, sin desinstalar.
