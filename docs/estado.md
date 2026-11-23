# SocialHub · estado actual (para retomar)

Última actualización: 23 nov 2026, ~23:00 (tanda del martes aprobada y programada, perfiles mejorados, decisiones 30 a 32; antes: ~22:00, plan de la fase 13 cerrado con las respuestas de Raúl, decisiones 25 a 29; antes: ~21:30, copias del repositorio al día con las del proyecto de Claude y pruebas del lunes vistas; antes: 23 nov, ~10:50, revisión completa de la documentación y fuentes resueltas; 22 nov, cierre de la fase 12, plan de la fase 13 con sus dudas resueltas y fusión de la 13 y la 14). Cómo se trabaja: `docs/como-trabajar.md` (una fase por conversación).

**Orden de trabajo (Raúl, 22 nov):** pruebas de la fase 12 (en curso) → fase 13 entera, con la lista de pendientes como bloque 8 → prueba de cero. **No hay fase 14** (se fusionó en la 13 el 22 nov, ~23:00).

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
| 12 · Todo lo pendiente | `fase-12.md` | ✅ Código hecho y en `main` (PR #36 a #78). Pruebas de uso en curso; falta la «prueba de cero» con Raúl |
| 13 · Skills de diseño y contenido en el flujo de Claude, más pendientes | `fase-13.md` | Plan aprobado, con las dudas resueltas (decisiones 11 a 22). **Bloque 1 hecho y en `main`** (PR #81, 22 nov, 22:54: motor de plantillas SVG y fuentes Crimson Text, Fira Sans y Bebas Neue, que Raúl confirma el 23 nov; no se añade Lora). Faltan los bloques 2 a 5, 7 y 8 y el cierre, que empiezan después de ver la tanda del martes 24 nov |
| 14 | `fase-14.md` | Fusionada en la 13 (solo queda un aviso) |

## Fase 12: cerrada en código

Todo el código está en `main`. Últimas PR: #69 (arranque con Windows, bandeja, métricas de Instagram, «Renovar conexión», aviso de versión, textos a mano), #73 (pantalla final del instalador y lanzadores con aviso), #74 (módulo de ajustes, margen mínimo ajustable de 5 min a 24 h, fondos SVG, fondo propio de SocialHub), #75 (margen de TikTok aparte, 8 h), #76 (estado de publicaciones, duplicados, «Mover»), #77 (respuestas de Claude, citas verificadas, enlaces, «este texto ya se usó»; migraciones V31 y V32) y #78 (aviso del margen de TikTok, color de marca obligatorio, duplicados más estrictos). Después, en la fase 13: #81 (bloque 1). Detalle en `fase-12.md` y `decisiones.md`.

Ojo: lo que no se ha probado en real (publicaciones reales, tareas de Claude con las reglas nuevas) no está dado por hecho.

## Pruebas de uso en Chrome (22-23 nov)

El 22 nov se probó SocialHub 1.2.61 con El DeLito y «Primera»: «Prueba 1» (Instagram, API oficial, lun 23 nov 15:00) y «Prueba 2» (TikTok, Publer, lun 23 nov 19:00), ambas programadas. Hallazgos en `traspaso-pruebas.md`; los que hay que arreglar están ya en la fase 13 (bloques 3, 4 y 7). Lo que salga de la prueba real del lunes se suma después.

**Resultado del lunes 23 nov:** la Prueba 1 (Instagram, API oficial) se publicó a las 15:01, sin música; la Prueba 2 (TikTok, Publer) se publicó hacia las 19:00 y la tarea de Chrome la cerró como «Publicada» a las 20:57. Ambas bien y sin la palabra «prueba» en la red. **Tanda del martes 24 nov:** se prepararon 10 publicaciones de «Primera» (5 de Instagram por API oficial: 2 carruseles, 2 historias, 1 reel; 5 de TikTok por Publer: 3 carruseles, 2 reels), de 19:00 a 21:00 cada 30 minutos (ids `2026-11-24-instagram` a `-5` y `2026-11-24-tiktok` a `-5`), con piezas, spec y textos generados y todas en «En producción». **Aprobadas esa misma noche (23 nov, ~23:00):** Raúl pidió que Claude las revisara y aprobara con Chrome y eligió «aprobar y programar las 10». Comprobado por la API: las 10 están en «Programada» (19:00, 19:30, 20:00, 20:30 y 21:00, una de Instagram y una de TikTok en cada hora), sin avisos de calidad abiertos ni alertas y sin música de Litoques (decisión 23). Defecto aceptado: en la historia instagram-4 la llamada a la acción en rojo se lee mal sobre la letra manuscrita. **Perfiles:** Raúl dio permiso a Claude para mejorar los de Instagram (@elde_lito, @litoques) y TikTok (@litoques.eldelito); Claude cambió la foto y la descripción de TikTok y la descripción de @litoques; @elde_lito, solo revisado, sin cambios. Pendiente de Raúl: cuenta de empresa en TikTok para poner enlaces (decisión 32). Detalle en las decisiones 30 a 32. Hallazgos nuevos (numerados del 1 al 23) en `claude/hallazgos-23-nov.md` del proyecto de Claude; se pasan a la fase 13 (bloques 3, 4, 5 y 7) al empezar a programar.

## Situación de las cuentas y tareas (decisiones de Raúl)

- Las cuentas de Instagram (@litoques, @elde_lito) y el TikTok compartido son **de prueba**. Nunca se publica al instante.
- Las dos tareas programadas de Claude («Encargos de SocialHub» y «Publicar con Chrome») se habían borrado y se **recrearon el 22 nov para las pruebas de uso**: hoy están **activas** (comprobado 22 nov, ~23:15: las dos habilitadas y con su última ejecución correcta). Decisión de Raúl (22 nov): siguen activas hasta que acabe la tanda del martes 24 nov (decisión 25: la tanda las necesita); después se desactivan, preguntando antes a Raúl, y se **actualizan y reactivan al terminar la fase 13**. No se tocan sin su OK en el momento.
- Se cancelaron las 27 publicaciones de prueba y las tareas de espera de la app.

## Siguiente

1. **Pruebas reales del lunes 23 nov: hechas** (resultado arriba; «Publicada» confirmada en las dos). Queda ver cómo salen las 10 publicaciones de la tanda del martes 24 nov (aprobadas y programadas la noche del 23 nov) y sumar sus hallazgos a la fase 13 antes de programar (decisión 11). Las dos tareas programadas siguen activas y se desactivan cuando acabe la tanda, preguntando antes a Raúl (decisión 25).
2. **Fase 13** (`fase-13.md`): plan aprobado por Raúl. PR del plan (#80) y del bloque 1 (#81) mergeadas el 22 nov; las copias de `docs/` se ponen al día con esta PR (`fase-13-docs-al-dia`). Los bloques 2 a 5, 7 y 8 empiezan después de ver la tanda del martes (Raúl, 23 nov, ~21:20: esperar a verla; decisión 24). Orden sin cambios: 2, 3, 4, 5, 7, 8 y 6 (decisión 28). El disco Litoques aparece en las bibliotecas de Instagram y TikTok (decisión 26). Las publicaciones pasarán solas a «Borrador» cuando tengan piezas y texto (decisión 29, bloque 7). Qué entra del bloque 8 se decide al empezar ese bloque (decisión 27). Al terminarla, con el OK de Raúl, se actualizan y reactivan las dos tareas programadas.
3. **Prueba de cero, con Raúl, al acabar la fase 13** (una sola, con todo): desinstalar; carpetas de Drive nuevas (las viejas, renombradas con prefijo, y la segunda carpeta «publico»); configurar con Chrome; comprobar si las skills están disponibles en las tareas programadas y medir los tokens de un kit y de un spec; publicaciones reales de prueba en Instagram y TikTok respetando el margen.
4. **Pendientes de Raúl** (en el bloque 8, apartado 8.2): icono `.ico` del instalador, permiso `instagram_manage_insights` de Meta, publicar una release para probar el aviso de versión, y confirmar si regeneró la clave de Meta y si sigue vigente comparar el portátil con la torre HP. Buffer: descartado para siempre.

**Revisión de pendientes (23 nov, ~10:45):** se leyeron todos los documentos del proyecto (salvo los prototipos HTML) y se contrastó el borrador original `socialhub.txt` con lo construido. Salieron tres ideas del borrador sin decisión (Whisper.cpp, presupuestos y paradas de seguridad, varias instalaciones con exclusión), ahora en `fase-13.md`, bloque 8.3, números 25 a 27, y varias copias desfasadas (se corrigen en el cierre). La contradicción de las fuentes (plan «solo Lora» frente a las tres de `main`) quedó resuelta con Raúl. Del repositorio se leyeron solo los documentos principales; lo demás, con búsquedas de texto. **No se puede asegurar que no haya más pendientes en el código.** Detalle en `fase-13.md`, «Revisión de pendientes».
