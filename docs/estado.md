# SocialHub · estado actual (para retomar)

Última actualización: 22 nov 2026 (cierre de la fase 12 y plan de la fase 13). Cómo se trabaja: `docs/como-trabajar.md` (una fase por conversación).

## Dónde estamos

Las fases 0 a 12 están hechas y en `main`:

| Fase | Documento | Estado |
|---|---|---|
| 0 · Análisis y decisiones | `fase-0.md` | ✅ |
| 1 · Prototipo visual | `prototipos/` | ✅ Propuesta «Moderna» |
| 2 · Backend, persistencia y frontal | `fase-2.md` | ✅ |
| 3 · Motor de tareas y herramientas locales | `fase-3.md` | ✅ |
| 4 y 5 · Drive y encargos a Claude | `fase-4-5.md` | ✅ |
| 6 · Producción editorial | `fase-6.md` | ✅ |
| 7 · Publicación | `fase-7.md` | ✅ Publer, Publora y calendario |
| 8 · Métricas y autonomía | `fase-8.md` | ✅ |
| 9 · Copias e instaladores | `fase-9.md` | ✅ |
| 10 · Primera semana real | `fase-10.md` | ✅ |
| 11 · Arreglos de la primera semana | `fase-11.md` | ✅ |
| 12 · Todo lo pendiente | `fase-12.md` | ✅ Código hecho y en `main` (PR #36 a #78). Falta la «prueba de cero» con Raúl |

## Fase 12: cerrada en código

Todo el código está en `main`. Últimas PR: #69 (arranque con Windows, bandeja, métricas de Instagram, «Renovar conexión», aviso de versión, textos a mano), #73 (pantalla final del instalador y lanzadores con aviso), #74 (módulo de ajustes, margen mínimo ajustable de 5 min a 24 h, fondos SVG, fondo propio de SocialHub), #75 (margen de TikTok aparte, 8 h), #76 (estado de publicaciones, duplicados, «Mover»), #77 (respuestas de Claude, citas verificadas, enlaces, «este texto ya se usó»; migraciones V31 y V32) y #78 (aviso del margen de TikTok, color de marca obligatorio, duplicados más estrictos). Detalle en `fase-12.md` y `decisiones.md`.

Ojo: lo que no se ha probado en real (publicaciones reales, tareas de Claude con las reglas nuevas) no está dado por hecho.

## Situación de las cuentas y tareas (decisiones de Raúl)

- Las cuentas de Instagram (@litoques, @elde_lito) y el TikTok compartido son **de prueba**. Nunca se publica al instante.
- Se **borraron** las dos tareas programadas de Claude («Encargos de SocialHub» y «Publicar con Chrome»). Se recrearán con el texto de `docs/tarea-programada-encargos.md` y con el OK de Raúl, en la prueba de cero.
- Se cancelaron las 27 publicaciones de prueba y las tareas de espera de la app.

## Siguiente

1. **Fase 13 · Skills de diseño y contenido en el flujo de Claude** (`fase-13.md`): plan **aprobado por Raúl** (22 nov 2026) y PR del plan abierta. Siguen sus 6 bloques; el primero empieza proponiendo las fuentes.
2. **Prueba de cero, con Raúl, al acabar la fase 13** (una sola, con todo): desinstalar; carpetas de Drive nuevas (las viejas, renombradas con prefijo); configurar con Chrome; recrear las tareas de Claude ya con las skills; comprobar si las skills están disponibles en las tareas programadas y medir los tokens de un kit y de un spec; publicaciones reales de prueba en Instagram y TikTok respetando el margen.
3. Pendientes para después: aviso de publicaciones a la misma hora, lectura de Word/odt y OCR de texto en imágenes.
4. Siguen pendientes de Raúl (de antes): icono `.ico` propio del instalador y permiso `instagram_manage_insights` de Meta. Buffer: descartado para siempre.
