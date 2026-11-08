# SocialHub · Fase 9: distribución

Estado: **bloque 1 (copias) hecho** · 8 nov 2026. Falta el bloque 2 (instaladores).

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

## Bloque 2 · Instaladores (pendiente)

Workflow manual con jpackage para `.msi` y `.dmg`.
