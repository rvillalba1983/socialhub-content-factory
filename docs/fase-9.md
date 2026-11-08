# SocialHub · Fase 9: distribución

Estado: **copias e instalador de Windows hechos** · 8 nov 2026. Falta el `.dmg` de macOS.

Decisiones generales en `docs/fases-7-9.md`.

## Respuestas de Raúl (3 nov)

| # | Tema | Decisión |
|---|---|---|
| 1 | Cuándo se hace la copia | Al abrir SocialHub, si hoy no hay copia. Si sigue abierto de un día para otro, al cambiar de día. Se guardan las 14 últimas |
| 2 | Restaurar | Antes hace una copia de lo actual («antes-de-restaurar») y pide escribir el nombre de la copia para confirmar |
| 3 | Tras restaurar | SocialHub para el motor y los encargos y pide cerrarlo y volver a abrirlo; no se reinicia solo |
| 4 | Instaladores | `.msi` (Windows) y `.dmg` (macOS) con jpackage en GitHub Actions, sin firmar: Windows avisa con «Windows protegió tu PC» y macOS pide abrirlo con clic derecho la primera vez |
| 5 | Carpeta de copias | Una sola, en los ajustes generales de la app (no por marca) |
| 6 | Formato de la copia | El PostgreSQL incluido no trae `pg_dump`. SocialHub exporta cada tabla con el driver de PostgreSQL (`COPY`) a un `.zip`, sin parar nada. Para restaurar, recrea las tablas con las migraciones y vuelve a cargar los datos |

## Plan

1. **Copias:** módulo `backup`.
   - Ajuste «carpeta de copias» (tabla `app_setting`, V21).
   - Copia de la base real en un `.zip` con un CSV por tabla y un `manifest.json` con la versión de las migraciones, en una sola lectura coherente.
   - Rotación de las 14 últimas copias diarias.
   - Restauración: esquema temporal migrado a la versión de la copia → carga → migración a la última versión → sustitución en una transacción.
   - Parada con aviso (`AppModeHolder.halt`): ya está hecha, junto con el bloqueo de cambios en `RequestGuardFilter`.
   - Pantalla «Copias»: carpeta, lista, hacer copia ahora y restaurar.
2. **Instaladores:** workflow manual con jpackage para `.msi` y `.dmg`, con Java y PostgreSQL dentro, publicados en una release de GitHub.

## Bloque 1 · Copias (hecho, 8 nov)

- **Módulo `backup`** (hexagonal):
  - Dominio: nombres de copia, tipos y rotación.
  - Puertos de salida: archivos, base de datos y ajustes.
  - Adaptadores: carpeta de archivos, PostgreSQL con `COPY` y tabla `app_setting`.
  - Pantalla web: «Copias».
- **Copia:**
  - Un `.zip` con `tablas/<tabla>.csv` y `manifest.json` (versión de las migraciones, filas por tabla y valores de las secuencias).
  - Se hace en una transacción `REPEATABLE READ` de solo lectura, sin parar nada.
  - Nombre: `socialhub-AAAA-MM-DD-HHMMSS-<diaria|manual|antes-de-restaurar>.zip`. SocialHub solo toca los archivos con ese formato.
- **Cuándo:**
  - 20 s después de abrir SocialHub, si hoy no hay ninguna copia (en ninguna de las dos carpetas).
  - Después se comprueba cada 10 minutos, para cuando cambia el día.
- **Dónde:** en la carpeta elegida o, sin ella o si no está disponible, en `<datos>/copias`, con aviso en la armería («Necesita atención»). Se guardan las 14 más recientes de cada carpeta, sean del tipo que sean.
- **Restaurar:**
  1. Confirmación con el nombre de la copia.
  2. Copia «antes-de-restaurar».
  3. Esquema `restore_tmp` migrado a la versión de la copia: se vacían los datos iniciales de las migraciones, se cargan los CSV sin comprobar claves ajenas y se ajustan las secuencias.
  4. Migración a la última versión.
  5. `DROP SCHEMA public` + `RENAME` en una transacción.
  6. SocialHub queda parado (503 en los cambios y pantalla «SocialHub está parado»).

  Si algo falla antes del cambio, los datos siguen como estaban y SocialHub vuelve a trabajar.
- **Pruebas:**
  - Dominio.
  - Integración: copia → cambios → restaurar → vuelven los datos, las secuencias siguen y SocialHub queda parado.
  - Frontal.
  - Prueba a mano con la app: copia diaria sola, carpeta, copia ahora, restaurar y pantalla de parado.

## Bloque 2 · Instalador de Windows (hecho, 8 nov)

**Respuestas de Raúl:** de momento solo el `.msi`; el `.dmg` queda preparado y se lanza cuando se pase a probar en macOS. Para cerrar SocialHub, un botón en la web, sin icono en la bandeja.

- **Workflow `instaladores.yml`:**
  - Se lanza a mano con la versión, los sistemas y si se publica en una release. También corre en las PR que lo cambian, sin publicar.
  - jpackage crea el `.msi` con su Java dentro. PostgreSQL y FFmpeg van dentro del jar, en su versión de Windows.
  - Usa WiX 3.14, que es gratuito y ya viene en la máquina de Windows de GitHub.
- **Instalación:**
  - En `C:\Archivos de programa\SocialHub`, con acceso directo en el escritorio y en el menú Inicio. Pide permiso de administrador una vez.
  - La instalación solo para el usuario choca con una comprobación de WiX (ICE64) al usar otra carpeta, y la carpeta por defecto coincidiría con la de datos.
  - Los datos siguen en `%LOCALAPPDATA%\SocialHub`: no se borran al desinstalar ni al actualizar. El instalador de una versión nueva sustituye al anterior (mismo `UPGRADE_UUID`).
- **Modo instalado** (`-Dsocialhub.desktop=true`):
  - Al arrancar abre el navegador.
  - Si ya está abierto, el acceso directo solo abre el navegador.
  - Sin consola: el registro va a `%LOCALAPPDATA%\SocialHub\logs\socialhub.log`.
- **Cerrar SocialHub:**
  - Botón «Cerrar» en la barra, con confirmación, que llama a `POST /api/app/shutdown`.
  - También está en la pantalla «SocialHub está parado»: funciona aunque esté parado.
  - Cierra Spring, el motor, PostgreSQL y el servidor.
- **Pruebas:**
  - En la máquina de Windows de GitHub, el workflow:
    1. Instala el `.msi`.
    2. Abre SocialHub y comprueba que responde y sirve la web.
    3. Comprueba que un segundo arranque termina enseguida.
    4. Lo cierra desde la web y comprueba que se para PostgreSQL.
    5. Lo desinstala y comprueba que los datos siguen.
  - El mismo recorrido, probado a mano en Linux.
- **Sin firmar:** Windows avisa con «Windows protegió tu PC». Pulsa «Más información» y luego «Ejecutar de todas formas».

## Pendiente

- `.dmg` de macOS: cuando haya minutos de macOS y se pase a probar en macOS (ver `docs/decisiones.md`).
