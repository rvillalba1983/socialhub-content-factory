# SocialHub · estado actual (para retomar)

Última actualización: 22 nov 2026. Cómo se trabaja: `docs/como-trabajar.md` (una fase por conversación).

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
| 12 · Todo lo pendiente | `fase-12.md` | ✅ Hecha el 12 nov (PR #36 a #42). Falta la revisión de Raúl |

## Fase 12 en una línea por PR

- #36 plan · #37 instalador de macOS (`.dmg`) y versión 1.0.0 en todos los sistemas · #38 API oficial de Instagram (vía principal; Publer de respaldo) · #39 pruebas con reloj fijo · #40 detalles visuales de «Necesita atención» y «Qué hacer ahora» · #41 TikTok y cuenta de Publer por marca y red, con el límite de 10 del plan Free · #42 métricas reales de la promoción · #45 ritmo de Claude (pasadas cada 5 h, urgencia, spec por lotes; versión 1.1.0).
- 16 nov: rama `fase-12-material-faltante` (PR abierta): fondos por defecto y SVG de Claude, portada de marca, aviso previo, «Estilo visual» y margen mínimo de publicación ajustable (30 minutos por defecto, en lugar de 6 h). Detalle en `fase-12.md` y `decisiones.md`.
- 22 nov: **margen mínimo de publicación en TikTok** aparte del general (480 minutos por defecto; rama `fase-12-margen-tiktok`). Las tareas programadas siguen sin tocar: la de propuestas debería leer `inputs.tiktok_min_publish_margin_minutes` (`docs/tarea-programada-encargos.md`).
- El repositorio es público desde el 12 nov (GitHub Actions se había quedado sin minutos).
- Configurado fuera del código: Meta (app, porfolio, usuario del sistema), carpeta `publico` de Litoques en Drive, Publer con 3 cuentas, y las tareas «Encargos de SocialHub» y «Publicar con Chrome» actualizadas. Detalle en `fase-12.md`.

- Orden con la #45: **primero se instala la 1.1.0 y después se cambian las tareas programadas** (nube y PC) a los horarios y la regla de 08:00-18:00 nuevos; el detalle está en `docs/tarea-programada-encargos.md`.

## Funciones finales (16 nov)

PR `fase-12-funciones-finales` (pendiente de merge): arranque con Windows en segundo plano, icono en la bandeja, métricas de Instagram por la API de Meta, renovar conexión, aviso de versión nueva y «Textos» a mano que sustituyen el texto de Raúl. Detalle en `fase-12.md`.

- Publicaciones: aviso de duplicados con «Descartar esta», resultado de cada publicación (Publicada ✓ / Falló / Retrasada / Pendiente / Sin confirmar) y «Mover a la siguiente hora libre» (PR `fase-12-publicaciones-estado`, pendiente de revisión).

- Claude pregunta y Raúl contesta desde la tarjeta; las citas se comprueban contra los documentos de la promoción (PDFBox) y los enlaces https se prueban en el control de calidad (PR `fase-12-claude-preguntas-citas`, pendiente de revisión). Para que Claude use `human_answers` y `citations` basta con que el encargo llegue con las reglas nuevas (van dentro de cada encargo); el texto de la tarea programada se actualizó en `docs/tarea-programada-encargos.md` y no se ha tocado la tarea existente.

## Siguiente (para Raúl)

1. Instalar SocialHub 1.0.0 cuando se publique la release.
2. Pegar la clave de Meta en «Agentes y herramientas» (mejor regenerarla antes, porque pasó por el chat).
3. Poner en la ficha de cada marca el ID de su cuenta de Instagram y sus cuentas de Publer.
4. Hacer la primera publicación real por la API oficial (aún no se ha probado una completa).
5. Crear las carpetas de El DeLito en Drive y, con ellas, crear y compartir su `publico`.
6. Pruebas funcionales completas de Raúl.

Sigue pendiente de antes: carpeta base de Litoques y carpeta de copias, clave de Publora, y comprobar que las tareas programadas recogen los encargos de verdad. Fuera de fase: comparar el portátil con la torre HP de 2013 (`ficha-equipo.bat`). Buffer: descartado para siempre.
