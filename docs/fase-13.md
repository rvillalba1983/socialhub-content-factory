# Fase 13 · Skills de diseño y contenido en el flujo de Claude

Estado: **plan aprobado por Raúl** (22 nov 2026, con las respuestas de abajo). Todavía no hay código: el primer paso del bloque 1 es proponer las fuentes.

## Qué pidió Raúl

Añadir al flujo de creación de SocialHub dentro de Claude estas skills:

- **Vídeo:** `video-making` (escenas dibujadas con código y montadas con ffmpeg).
- **Imágenes para redes (HTML/SVG a PNG):** `carousel-writer` (Raúl ignora su parte de prompts para generadores de imagen), `quote-cards-and-text-graphics`, `infographic-and-data-viz`, `thumbnail-design`, `pinterest-pin-design`, `story-writer`, `tiktok-photo-mode` y `meme-and-culture` (memes basados en texto).
- **Apoyo (marca, diseño y texto):** `brand-profile` (se carga primero), `design-and-templates`, `platform-specs-and-validation`, `caption-writer`, `hook-writer`, `dataviz` y `frontend-design`.

## Decisiones de Raúl (22 nov 2026)

1. **Quién dibuja: plantillas una vez por marca y SocialHub dibuja.** Claude diseña una sola vez por marca un kit y unas plantillas SVG reutilizables; en cada publicación solo rellena textos en el spec, y SocialHub dibuja las piezas en local (Batik para SVG a PNG y FFmpeg para vídeo, gratis). Se descartó que Claude entregue los PNG/MP4: Raúl preguntó antes por el gasto de tokens y la estimación (no medida) era de 10 a 20 veces más por pieza (un carrusel de 8 slides, unos 40-60 mil tokens frente a los 3-6 mil de un spec de texto de hoy).
2. **Pinterest y miniaturas, fuera por ahora.** `pinterest-pin-design` y `thumbnail-design` se quedan sin usar: SocialHub solo publica en Instagram y TikTok. Se añadirán cuando Pinterest o YouTube sean una red de SocialHub. Entran las otras 13 skills.
3. **Fuentes: añadir 2 o 3 libres (OFL),** incluidas en la app. Hoy solo hay DejaVu Serif Bold e Italic. Antes de añadir ninguna, Claude propone cuáles y Raúl da el OK (licencia y peso).
4. **El kit y las plantillas viven en SocialHub, con aprobación de Raúl.** Se guardan en la base de datos de la marca, SocialHub las valida (SVG seguro, solo fuentes incluidas, campos conocidos) y en la ficha de la marca hay una vista previa de cada plantilla con textos de ejemplo. No se usan hasta que Raúl pulsa «Aprobar»; puede pedir otra versión a Claude con un comentario.
5. **Orden: fase 13 primero y una sola «prueba de cero» después.** Las tareas programadas de Claude (hoy borradas) se recrearán una sola vez, ya con las skills.
6. **`video-making` se aplica en el FFmpeg de SocialHub** (no en la sesión de Claude), como se proponía.
7. **`tiktok-photo-mode`: se mantiene el carrusel de hoy.** No hay tipo nuevo para el carrusel de fotos de TikTok; usa el mismo tipo `carrusel`. (Raúl contestó «se mantiene»; se interpreta como mantener lo que hay.)
8. **Tipos nuevos de pieza:** cita, infografía y meme de texto, además de los de hoy (carrusel, historia, reel, clip).
9. **Plan aprobado** y PR del plan abierta para que Raúl la una.

## Cómo encaja cada skill (propuesta técnica)

| Skill | Dónde se usa | Para qué |
|---|---|---|
| `brand-profile` | Todos los encargos de diseño y de texto | Se carga primero, pero **sin entrevista**: parte de `inputs.brand_identity`. Si faltan el tono o el público, `needs_human` (como hoy) |
| `design-and-templates`, `dataviz`, `frontend-design` | Encargo nuevo `socialhub.brand-kit` | Criterio visual para diseñar el kit (colores, jerarquía, tipografía) y las plantillas SVG; `dataviz` para los colores y la forma de gráficos e infografías |
| `carousel-writer`, `story-writer`, `tiktok-photo-mode` | `socialhub.weekly-spec` | Estructura y texto de cada slide de carruseles, historias y carruseles de fotos de TikTok; eligen plantilla y rellenan campos |
| `quote-cards-and-text-graphics` | `socialhub.weekly-spec` | Tarjetas de cita. Los versos van **literales** y con `citations` (regla de siempre) |
| `infographic-and-data-viz` | `socialhub.weekly-spec` | Infografías y gráficos de datos. Solo con datos que consten en los documentos de la promoción; sin cifras inventadas |
| `meme-and-culture` | `socialhub.weekly-spec` | Memes solo de texto, sobre plantillas del kit. Sin imágenes ni personajes de terceros |
| `hook-writer`, `caption-writer` | `socialhub.weekly-spec` y `socialhub.captions` | Gancho de la portada y textos y hashtags |
| `platform-specs-and-validation` | Spec, textos y revisión de calidad | Solo la parte de tamaños y límites de Instagram y TikTok. Su flujo de validación (`POST /posts/validate`) es de otro servicio y no se usa |
| `video-making` | Montaje de vídeo en SocialHub | Es una guía de cómo montar el vídeo (una escena por archivo con los mismos parámetros, unirlas con concat sin recodificar, sonido después). La aplica SocialHub en su FFmpeg, no Claude en la tarea (confirmado por Raúl) |

Reglas fijas para todas:
- **Se ignora la parte de generación de imágenes con IA** (prompts para Nano Banana, Ideogram o similares) en todas las skills. Nada de imágenes generadas con IA.
- **Versos y letras, siempre literales** y verificados contra los documentos (citas), también en tarjetas de cita y memes.
- **Color de marca obligatorio** (ya lo es): sin color, no se dibuja.
- **Pinterest y YouTube, fuera.** Si una skill propone formatos de esas redes, se ignoran.

### Cómo llegan las skills a la tarea de la nube

- SocialHub escribe en cada encargo `inputs.skills`: solo las que hacen falta según los tipos de pieza del spec (por ejemplo, un spec solo de carruseles no carga la de memes). Menos skills cargadas, menos tokens.
- El subagente las carga con la herramienta Skill. Si alguna no existe en la sesión de la tarea programada, sigue sin ella y lo dice en `warnings`; el spec sale igual con reglas mínimas, que viajan en el propio encargo (como las `AiStandingRules` de hoy).
- **No se copian skills al repositorio:** `video-making` tiene licencia propietaria de Anthropic y no se puede redistribuir; el resto se cargan de la cuenta de Raúl, y si no están en la tarea programada se sigue sin ellas (ver arriba).
- **Pendiente de comprobar en la prueba de cero:** si las skills del plugin `social-media-skills` están disponibles en las sesiones de las tareas programadas. Hasta recrearlas no se puede saber.

### Control de tokens

- El diseño caro se paga **una vez por marca** (`socialhub.brand-kit`); cada spec solo rellena textos.
- Cada encargo carga solo las skills que necesita.
- Modelo por defecto del kit: Sonnet (sube un nivel tras dos fallos, como los demás encargos). El spec mantiene el suyo.
- El kit se pide solo cuando la marca no tiene uno aprobado, o a mano desde la ficha; nunca de forma recurrente.
- Las cifras de tokens de arriba son estimaciones: en la prueba de cero se mide el gasto real de un kit y de un spec.

## Bloques (una rama y una PR por bloque)

| # | Bloque | Rama | Qué incluye |
|---|---|---|---|
| 1 | Fuentes y motor de plantillas | `fase-13-plantillas` | Proponer y añadir las 2-3 fuentes libres (con OK). Motor de plantillas SVG: campos con nombre, texto que ajusta líneas y tamaño para no salirse de la caja, Batik con las fuentes incluidas. Vista previa. Reutiliza la validación de SVG seguro de `fondo_svg` |
| 2 | Kit de marca | `fase-13-kit-marca` | Tabla nueva (migración), contrato del kit, encargo `socialhub.brand-kit` y su manejador, vista previa en la ficha de la marca con «Aprobar» y «Pedir otra versión», recomendación «Genera el kit de X» en «Qué hacer ahora» |
| 3 | Spec con plantillas y piezas nuevas | `fase-13-spec` | `promo-spec` ampliado sin cambiar de versión: piezas nuevas (cita, infografía, meme) y `plantilla` + campos. `PieceGenerator` usa las plantillas aprobadas. Sin kit aprobado, todo sigue como hoy |
| 4 | Skills en los encargos | `fase-13-skills` | `inputs.skills` por encargo, reglas fijas, `caption-writer` y `hook-writer` en los textos, y texto de las tareas programadas en `docs/tarea-programada-encargos.md` (sin tocar las tareas) |
| 5 | Vídeo con escenas | `fase-13-video` | Reels y clips a partir de escenas de plantilla (duración y transición por escena), montados con FFmpeg según `video-making` |
| 6 | Cierre | `fase-13-cierre` | Pruebas (unitarias, e2e de la ficha del kit), manual y tutorial, estado, decisiones y copia al proyecto |

Orden: 1, 2, 3, 4, 5, 6. Cada bloque pasa en GitHub antes de seguir y Raúl hace el merge.

## Qué no entra

- Pinterest y YouTube (pines y miniaturas).
- Generación de imágenes con IA y que Claude entregue PNG o MP4.
- Subir portadas de reel a las redes.
- Cambios en la publicación (vías, márgenes) y recrear las tareas programadas (se hace en la prueba de cero, con el OK de Raúl).

## Dudas resueltas y pendientes

Resueltas por Raúl el 22 nov (decisiones 6 a 8): `video-making` en el FFmpeg de SocialHub; el carrusel de fotos de TikTok usa el carrusel de hoy; los tipos nuevos son cita, infografía y meme de texto.

Pendiente: **las fuentes concretas.** Se proponen al empezar el bloque 1 y no se añade ninguna sin el OK de Raúl.
