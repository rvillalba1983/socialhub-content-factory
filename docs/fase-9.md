# SocialHub · Fase 9: distribución

Estado: **preguntas respondidas; empezado** (rama `fase-9/copias`, sin terminar) · 3 nov 2026. Se sigue el domingo.

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

## Hecho hasta ahora (sin probar ni PR)

- V21 `app_setting`.
- `AppModeHolder.halt`/`haltedReason`: el trabajo en segundo plano no corre si SocialHub está parado.
- `RequestGuardFilter` responde 503 a los cambios mientras está parado.
