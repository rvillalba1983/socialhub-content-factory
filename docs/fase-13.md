# Fase 13 · Skills de diseño y contenido en el flujo de Claude, más la lista de pendientes (antes «fase 14»)

Estado: **plan aprobado por Raúl** (22 nov 2026, con las respuestas de abajo). **Bloque 1 ya hecho y en `main`** (PR #81, mergeada el 22 nov a las 22:54: motor de plantillas SVG y las fuentes Crimson Text, Fira Sans y Bebas Neue). **Fuentes resueltas el 23 nov, ~10:50:** el plan decía «solo Lora» (decisión 14) y el repositorio ya tenía otras tres; Raúl elige **las tres de `main`** y no se añade Lora. **22 nov, 22:25: Raúl pide pasar a esta fase los hallazgos de las pruebas de uso** (sección «Arreglos de las pruebas de uso» y bloque 7). **22 nov, ~22:45: Raúl contesta las 12 dudas abiertas (decisiones 11 a 21).** **22 nov, ~23:00: Raúl pide juntar la fase 13 y la 14 (decisión 22): ya no hay fase 14, sus pendientes son el bloque 8.** **23 nov, ~15:35: Raúl pide música de Litoques en las publicaciones de prueba y, si no se puede hoy, dejarla para esta fase (decisión 23).** El resto de bloques empieza **después de ver el resultado de las pruebas reales del lunes 23 nov** (vistas: Prueba 1 y Prueba 2 salieron bien) **y de la tanda del martes 24 nov (decisión 24, Raúl, 23 nov, ~21:20)**. **23 nov, ~22:00: Raúl responde las preguntas de cierre del plan (decisiones 25 a 29).** **23 nov, noche: Claude revisa, aprueba y programa con Chrome la tanda del 24 nov (decisión 30) y mejora los perfiles de Instagram y TikTok (decisión 31); la cuenta de empresa en TikTok queda pendiente de Raúl (decisión 32).** Orden: pruebas de la fase 12 (en curso) → fase 13 entera (bloques 1 a 8 y cierre) → prueba de cero. La lista completa de hallazgos, con su numeración, sigue en `claude/traspaso-pruebas.md`.

## Qué pidió Raúl

Añadir al flujo de creación de SocialHub dentro de Claude estas skills:

- **Vídeo:** `video-making` (escenas dibujadas con código y montadas con ffmpeg).
- **Imágenes para redes (HTML/SVG a PNG):** `carousel-writer` (Raúl ignora su parte de prompts para generadores de imagen), `quote-cards-and-text-graphics`, `infographic-and-data-viz`, `thumbnail-design`, `pinterest-pin-design`, `story-writer`, `tiktok-photo-mode` y `meme-and-culture` (memes basados en texto).
- **Apoyo (marca, diseño y texto):** `brand-profile` (se carga primero), `design-and-templates`, `platform-specs-and-validation`, `caption-writer`, `hook-writer`, `dataviz` y `frontend-design`.

Y, después, que las cosas pendientes que hay apuntadas (lista de la antigua fase 14) vayan en esta misma fase.

## Decisiones de Raúl (22 nov 2026)

1. **Quién dibuja: plantillas una vez por marca y SocialHub dibuja.** Claude diseña una sola vez por marca un kit y unas plantillas SVG reutilizables; en cada publicación solo rellena textos en el spec, y SocialHub dibuja las piezas en local (Batik para SVG a PNG y FFmpeg para vídeo, gratis). Se descartó que Claude entregue los PNG/MP4: Raúl preguntó antes por el gasto de tokens y la estimación (no medida) era de 10 a 20 veces más por pieza (un carrusel de 8 slides, unos 40-60 mil tokens frente a los 3-6 mil de un spec de texto de hoy).
2. **Pinterest y miniaturas, fuera por ahora.** `pinterest-pin-design` y `thumbnail-design` se quedan sin usar: SocialHub solo publica en Instagram y TikTok. Se añadirán cuando Pinterest o YouTube sean una red de SocialHub. Entran las otras 13 skills.
3. **Fuentes: añadir 2 o 3 libres (OFL),** incluidas en la app. Antes de añadir ninguna, Claude propone cuáles y Raúl da el OK (licencia y peso). *Ver decisión 14: son tres, las que ya están en `main`.*
4. **El kit y las plantillas viven en SocialHub, con aprobación de Raúl.** Se guardan en la base de datos de la marca, SocialHub las valida (SVG seguro, solo fuentes incluidas, campos conocidos) y en la ficha de la marca hay una vista previa de cada plantilla con textos de ejemplo. No se usan hasta que Raúl pulsa «Aprobar»; puede pedir otra versión a Claude con un comentario.
5. **Orden: fase 13 primero y una sola «prueba de cero» después.** Las tareas programadas de Claude se recrearán una sola vez, ya con las skills. *Ver decisión 15: se actualizan y reactivan al terminar la fase 13.*
6. **`video-making` se aplica en el FFmpeg de SocialHub** (no en la sesión de Claude), como se proponía.
7. **`tiktok-photo-mode`: se mantiene el carrusel de hoy.** No hay tipo nuevo para el carrusel de fotos de TikTok; usa el mismo tipo `carrusel`. (Raúl contestó «se mantiene»; se interpreta como mantener lo que hay.)
8. **Tipos nuevos de pieza:** cita, infografía y meme de texto, además de los de hoy (carrusel, historia, reel, clip). *Ver decisión 13: para El DeLito solo se activa la cita en el primer kit.*
9. **Plan aprobado** y PR del plan abierta para que Raúl la una.
10. **Los hallazgos de las pruebas de uso se resuelven dentro de la fase 13** (22 nov, 22:25): los de spec y encargos van a los bloques 3 y 4, y el resto a un bloque nuevo, el 7.

Respuestas a las 12 dudas (22 nov, ~22:45):

11. **Cuándo empezar:** después de ver el resultado de las pruebas reales del lunes 23 nov (PC, API oficial y TikTok), para que sus hallazgos entren en el plan antes de tocar código.
12. **Formato de TikTok: 9:16.** El spec y `PieceGenerator` eligen el tamaño según la red; los carruseles de TikTok dejan de salir en 4:5 y de exigir 4 aceptaciones a mano (arreglo B).
13. **Piezas nuevas para El DeLito en el primer kit: solo la cita** (verso literal sobre fondo, con su cita comprobada contra el PDF). Infografía y meme de texto no se activan por ahora; el contrato los admite para activarlos después.
14. **Fuentes: Crimson Text, Fira Sans y Bebas Neue** (las tres, OFL, unos 2,4 MB, ya incluidas en `main` con su licencia: Crimson Text serif de libro para versos y citas, Fira Sans para textos, cifras e infografías, Bebas Neue para títulos en mayúsculas). DejaVu Serif se queda. **Historia:** el 22 nov, ~22:45, se había anotado «Lora» (se ofrecieron además Playfair Display, Caveat y Nunito; no marcó ninguna más), pero el repositorio ya incluía estas tres, aprobadas por Raúl el 22 nov y mergeadas en la PR #81 a las 22:54, y su `docs/decisiones.md` explica que se descartaron las fuentes variables (Lora, Playfair, Montserrat…) porque Java 2D y Batik solo usan su instancia por defecto. **El 23 nov, ~10:50, Raúl confirma las tres de `main`; no se añade Lora.**
15. **Tareas programadas** («Encargos de SocialHub» y «Publicar con Chrome»): **siguen activas hasta terminar las pruebas del lunes 23 nov** (las necesitan); **después se desactivan** y **se actualizan y reactivan al terminar la fase 13** (Raúl, 22 nov: primero dijo «al terminar la fase 14» y después lo cambió a la fase 13, al elegir la opción recomendada). No se tocan sin su OK en el momento. Así se puede comprobar en la prueba de cero si las skills llegan a las tareas y medir los tokens.
16. **Margen: se deja la norma de 6 h y se corrige el manual** (dice 30 min). La ficha explica el margen de cada vía (Publer 8 h, API oficial con imágenes entre 12,6 y 18,5 h). La lógica no cambia (arreglo D).
17. **Publora: marcar siempre como contenido IA en El DeLito** (`aiGenerated: true`), porque las ilustraciones del libro están hechas con IA.
18. **Rechazos: no se envían a Claude las cancelaciones de limpieza masiva** («Limpieza antes de la prueba de cero»); sí los rechazos con motivo propio (arreglo I).
19. **CTA: manda el de la marca** (`brand_identity.call_to_action`) en el spec y en los textos; un solo CTA para todas las piezas (arreglo J).
20. **Imagen y poema: el spec dice qué página del PDF y qué archivo `page NN.png` usa cada diapositiva, y se ve en la ficha**, para revisar de un vistazo que casan antes de aprobar (arreglo K).
21. **Audio de los reels:** «de carpeta Drive o seleccionable en las redes sociales» (respuesta literal de Raúl). Se interpreta así: si el spec nombra un tema de Litoques, SocialHub lo mezcla con fundidos desde una carpeta de Drive; si no, el reel sale sin sonido y el sonido se elige en la red. **A confirmar en el bloque 5** si la API oficial y Publer permiten elegir sonido de la biblioteca (para TikTok, Raúl ya pidió no añadir música desde Publer). *Ver decisión 23: se amplía a carruseles y se pide música de Litoques elegida en la red.*

Decisión de las 23:00:

22. **Se juntan la fase 13 y la 14** (Raúl, 22 nov, ~23:00). No habrá fase 14: la lista de pendientes pasa a esta fase como bloque 8, antes del cierre. El orden queda: pruebas de la fase 12 → fase 13 (bloques 1 a 5, 7, 8 y cierre) → prueba de cero. `claude/fase-14.md` queda solo como aviso de que se fusionó aquí.

Decisión del 23 nov, ~15:35 (tras ver la Prueba 1 sin música):

23. **Música de Litoques en las publicaciones, elegida en la propia red.** Raúl vio que la Prueba 1 salió sin música y pide que, en las siguientes pruebas, se use música del disco Litoques «seleccionándolo desde las redes sociales». Si SocialHub no lo contempla, se deja para la siguiente fase de desarrollo (esta). **Qué hay hoy (revisado en repositorio y documentos):** SocialHub no tiene ningún campo ni opción de música o sonido en una publicación; lo único que existe es la línea de texto «Música: <tema>, de Litoques» en la pieza (prueba unitaria de `PieceGenerator`) y la instrucción fija a Publer en TikTok de **no** añadir música (`PublerScheduleHandler`, `tarea-programada-encargos.md`, `TUTORIAL.md`, decisión de Raúl del 11-12 nov). **Qué entra en la fase 13:** (a) un campo de sonido en el spec y en la ficha de la publicación («sin sonido», «tema de Litoques en el vídeo» o «elegir en la red: <nombre del tema>»), con el nombre del tema para que Raúl o Claude lo busquen; (b) para TikTok, quitar la instrucción de «no añadir música» cuando la publicación pida sonido de la red; (c) comprobar en el bloque 5 qué permite cada vía: la API oficial de Instagram no tiene parámetro de música para carruseles de fotos (a confirmar en la documentación de Meta), Publer dice que los sonidos tienen que ir en el vídeo (`fase-12.md`) y no está comprobado que el catálogo de TikTok o Instagram tenga los temas de Litoques ni que una cuenta personal o de empresa pueda usarlos. **Sin comprobar:** si el disco está en la biblioteca de sonidos de cada red. Si una vía no permite elegir sonido, la alternativa de la decisión 21 es mezclar el tema desde una carpeta de Drive en un vídeo montado por SocialHub.

Decisión del 23 nov, ~21:20 (al empezar la fase en una conversación nueva):

24. **El código empieza después de ver la tanda del martes 24 nov** (Raúl, 23 nov, ~21:20, entre «plan hoy y bloque 2 ya», «esperar a mañana» y «solo docs al día»: eligió esperar). Hoy solo se ponen al día las copias de `docs/` (PR `fase-13-docs-al-dia`) y se cierra el plan con sus respuestas. Los hallazgos de la tanda se suman al plan antes de programar.

Respuestas del 23 nov, ~21:30 a ~22:00 (cierre del plan en la conversación nueva):

25. **Las tareas programadas siguen activas hasta que acabe la tanda del martes 24 nov** (Raúl, 23 nov). La decisión 15 decía desactivarlas al acabar las pruebas del lunes, pero la tanda de mañana las necesita: Instagram usa los enlaces públicos de Drive con el encargo `public-link` y TikTok usa Publer con Chrome. Se desactivan cuando acabe la tanda, preguntando antes a Raúl en ese momento; se actualizan y reactivan al terminar la fase 13 (decisión 15, sin cambios en eso).
26. **El disco Litoques aparece en las bibliotecas de sonidos de Instagram y de TikTok** (Raúl, 23 nov, buscando «Litoques» en el selector de sonidos de cada red). El campo de sonido «elegir en la red: <tema>» (decisión 23) tiene sentido en las dos. Sigue sin comprobar, en el bloque 5, si cada vía (API oficial de Instagram para carruseles de fotos, y Publer) permite elegir el sonido; si no, plan B: mezclar los MP3 masterizados de Litoques en el vídeo (decisiones 21 y 23).
27. **Bloque 8:** qué de lo «por decidir» (8.3, incluidos 25 a 27) entra en la fase 13, y qué falta exactamente en el aviso de publicaciones a la misma hora (8.1), se decide al empezar el bloque 8 (Raúl, 23 nov, opción recomendada).
28. **Orden de bloques sin cambios: 2, 3, 4, 5, 7, 8 y 6** (Raúl, 23 nov). Se revisa el 24 nov con la tanda delante: si sale algo que duela cada semana, se adelanta el bloque 7.
29. **Las publicaciones pasan solas de «En producción» a «Borrador» cuando tienen piezas y texto** (Raúl, 23 nov, opción recomendada). Solo ese paso (no publica nada): Pendiente de revisión, Aprobada y Programada siguen siendo manuales. Entra en el bloque 7.
30. **Tanda del martes 24 nov aprobada y programada** (Raúl, 23 nov, noche: «aprobar y programar las 10»; 10 en «Programada», sin avisos abiertos; quitado «sin música/sin sonido» del texto público y aceptados los avisos 4:5 de TikTok por decisión técnica de Claude). Ver `decisiones.md`.
31. **Perfiles de Instagram y TikTok** (Raúl, 23 nov, noche: «eres libre de mejorar los perfiles como mejor creas»). Claude cambió foto y descripción de TikTok y la descripción de @litoques y de @elde_lito (con el permiso de Raúl para cambiar de cuenta en Chrome, 23 nov, 22:34). TikTok no se pasa a cuenta de empresa (decisión técnica).
32. **Pendiente de Raúl: cuenta de empresa en TikTok para poder poner enlaces.** Sin decidir.

## Cómo encaja cada skill (propuesta técnica)

| Skill | Dónde se usa | Para qué |
|---|---|---|
| `brand-profile` | Todos los encargos de diseño y de texto | Se carga primero, pero **sin entrevista**: parte de `inputs.brand_identity`. Si faltan el tono o el público, `needs_human` (como hoy) |
| `design-and-templates`, `dataviz`, `frontend-design` | Encargo nuevo `socialhub.brand-kit` | Criterio visual para diseñar el kit (colores, jerarquía, tipografía) y las plantillas SVG; `dataviz` para los colores y la forma de gráficos e infografías |
| `carousel-writer`, `story-writer`, `tiktok-photo-mode` | `socialhub.weekly-spec` | Estructura y texto de cada slide de carruseles, historias y carruseles de fotos de TikTok; eligen plantilla y rellenan campos |
| `quote-cards-and-text-graphics` | `socialhub.weekly-spec` | Tarjetas de cita. Los versos van **literales** y con `citations` (regla de siempre) |
| `infographic-and-data-viz` | `socialhub.weekly-spec` | Infografías y gráficos de datos. Solo con datos que consten en los documentos de la promoción; sin cifras inventadas. No se activan para El DeLito en el primer kit (decisión 13) |
| `meme-and-culture` | `socialhub.weekly-spec` | Memes solo de texto, sobre plantillas del kit. Sin imágenes ni personajes de terceros. No se activan para El DeLito en el primer kit (decisión 13) |
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
- **Pendiente de comprobar en la prueba de cero:** si las skills del plugin `social-media-skills` están disponibles en las sesiones de las tareas programadas. Hasta actualizar y reactivar las tareas (al terminar la fase 13) no se puede saber.

### Control de tokens

- El diseño caro se paga **una vez por marca** (`socialhub.brand-kit`); cada spec solo rellena textos.
- Cada encargo carga solo las skills que necesita.
- Modelo por defecto del kit: Sonnet (sube un nivel tras dos fallos, como los demás encargos). El spec mantiene el suyo.
- El kit se pide solo cuando la marca no tiene uno aprobado, o a mano desde la ficha; nunca de forma recurrente.
- Las cifras de tokens de arriba son estimaciones: en la prueba de cero se mide el gasto real de un kit y de un spec.

## Arreglos de las pruebas de uso (22 nov 2026)

Salen de las pruebas de uso en Chrome con El DeLito y «Primera» (SocialHub 1.2.61, «Prueba 1» en Instagram y «Prueba 2» en TikTok). Ninguno bloqueó el trabajo: todos tenían salida a mano. La columna «Hallazgo» es el número en `claude/traspaso-pruebas.md`. Los marcados «Decidido» tienen ya respuesta de Raúl (decisiones 11 a 21).

**Prioridad alta (afectan a cada semana de trabajo)**

| # | Qué pasa y qué se propone | Hallazgo | Bloque |
|---|---|---|---|
| A | **Citas no reconocidas.** Si el resultado de textos devuelve `source_file` con formato libre («page 11-finalv2.pdf (Google Drive, carpeta «info», id …)»), el control de calidad dice «Cita no encontrada» aunque el verso sea literal. Propuesta: el control de calidad acepta el nombre del archivo aunque lleve añadidos, y el encargo pide siempre la ruta completa de la carpeta de la promoción | 13 | 4 (encargo) y 7 (control de calidad) |
| B | **Formato por red.** Para TikTok el control de calidad pide vertical 9:16 y las piezas del spec salen en 1080×1350 (4:5): cada carrusel de TikTok exige 4 aceptaciones a mano. **Decidido: 9:16 para TikTok;** el spec y `PieceGenerator` eligen el tamaño según la red y el tipo de pieza | 14 | 3 |
| C | **El encargo a Publer arrastra el título y la nota interna** («Prueba 2 …», «Prueba de TikTok …»), aunque las instrucciones solo mandan escribir texto, hashtags y archivos. Hoy no debería salir en TikTok, pero es un riesgo para la regla «la palabra prueba no sale en las redes». Propuesta: el encargo `publer-schedule` no lleva la nota interna ni el título de trabajo, y se comprueba con un test que ningún campo que se escriba en la red sale del título o de la nota | 19 | 4 |
| D | **Márgenes poco claros.** La ficha dice «6 horas», el manual dice 30 minutos, la API oficial con imágenes necesita entre 12,6 y 18,5 h y Publer 8 h. La app no explica por qué no se puede publicar ahora. **Decidido: se deja 6 h y se corrige el manual;** la ficha explica el margen de cada vía y por qué. **No cambia la lógica de márgenes** | 9 y 17 | 7 |

**Pantalla y avisos**

| # | Qué pasa y qué se propone | Hallazgo | Bloque |
|---|---|---|---|
| E | El editor se cierra solo al cambiar de estado: de Borrador a Programada hay que reabrirlo cuatro veces. Propuesta: que se quede abierto | 18 | 7 |
| F | Si se cambia el filtro de «Publicaciones» con un editor abierto, el editor se queda con los campos en blanco (Instagram, sin título). Propuesta: cerrarlo o recargarlo con el filtro nuevo | 16 | 7 |
| G | «Enlace OK» sale como aviso naranja aunque no bloquea, y la app no enseña «Verificada / Sin verificar» en las citas. Propuesta: el enlace correcto se muestra como correcto y cada cita enseña su estado | 7 y 8 | 7 |
| H | El contador de la estación «Programación» sigue en 21 tareas aunque todas están canceladas. Propuesta: contar solo las vivas | 1 | 7 |
| L | **Aviso de resultado que no llegó, por una tarea cancelada** (añadido al revisar el 22 nov, ~23:15). El mensaje «No ha llegado el resultado de los encargos de las 20:30» salió por una revisión de calidad que luego se canceló. Propuesta (a concretar con Raúl en el bloque): que un encargo cancelado no cuente para ese aviso | 5 | 7 |

**Encargos y datos para Claude**

| # | Qué pasa y qué se propone | Hallazgo | Bloque |
|---|---|---|---|
| I | Las 11 cancelaciones de «Limpieza antes de la prueba de cero» viajan a Claude como «Rechazos» en cada encargo de textos: ruido y gasto de tokens. **Decidido: no enviar los rechazos de limpieza masiva;** sí los que tengan motivo propio | 2 | 4 |
| J | El texto del spec y el del encargo de textos para la misma publicación son distintos (por ejemplo, el CTA: el spec llevaba el enlace de Amazon y los textos el de books2read de la marca). **Decidido: manda el CTA de la marca** (`brand_identity.call_to_action`) en spec y textos | 3 y 10 | 4 |
| K | Claude no ve los PNG: el emparejado imagen/poema (pág. 11 → `page 12.png`) es una suposición. Raúl lo confirmó el 22 nov (todo bien). **Decidido: el spec dice qué página del PDF y qué `page NN.png` usa cada diapositiva, y se ve en la ficha** | 4 | 3 |

**Con decisión de Raúl o a mano**

- **Publora (segunda vía):** recibía `aiGenerated: false` y `videoType: REELS` por defecto. **Decidido: marcar siempre como contenido IA en El DeLito** (`aiGenerated: true`). Falta revisar `videoType: REELS` por defecto. Bloque 7.
- **Dos carpetas llamadas «publico» en Drive** (la de El DeLito y otra, id `1N4bTqVbfKCDniRMbEGjwkWGsnBA0Hqp0`): renombrar con prefijo o retirar la otra, sin borrar. Se hace a mano con Raúl en la prueba de cero (hallazgo 15); no lleva código.
- **Proceso:** la PR #73 se mergeó con solo la CI de instalador y mapa. En la PR de cada bloque, comprobar que ha corrido toda la CI antes del merge (hallazgo 6).

**Todavía sin verificar:** la prueba real del lunes 23 nov (el PC programa la Prueba 2 en Publer de madrugada; la API oficial publica la Prueba 1 a las 15:00; la Prueba 2 sale en TikTok a las 19:00) puede añadir hallazgos. Se sumarán a esta lista después, y la fase 13 empieza una vez vistos (decisión 11). **Ya añadido (23 nov, 15:35):** la Prueba 1 salió bien en Instagram pero sin música (decisión 23).

## Bloques (una rama y una PR por bloque)

| # | Bloque | Rama | Qué incluye |
|---|---|---|---|
| 1 | Fuentes y motor de plantillas | `fase-13-plantillas` | ✅ **Hecho y en `main`** (PR #81, 22 nov, 22:54) con Crimson Text, Fira Sans y Bebas Neue (decisión 14). Motor de plantillas SVG: campos con nombre, texto que ajusta líneas y tamaño para no salirse de la caja, Batik para el dibujo y Java 2D para el texto con las fuentes incluidas. Vista previa. Reutiliza la validación de SVG seguro de `fondo_svg`. Formato en `docs/plantillas-svg.md` del repositorio |
| 2 | Kit de marca | `fase-13-kit-marca` | Tabla nueva (migración), contrato del kit, encargo `socialhub.brand-kit` y su manejador, vista previa en la ficha de la marca con «Aprobar» y «Pedir otra versión», recomendación «Genera el kit de X» en «Qué hacer ahora». Primer kit de El DeLito con la **cita** como pieza nueva (decisión 13) |
| 3 | Spec con plantillas y piezas nuevas | `fase-13-spec` | `promo-spec` ampliado sin cambiar de versión: piezas nuevas (cita; infografía y meme en el contrato, sin activar para El DeLito) y `plantilla` + campos. `PieceGenerator` usa las plantillas aprobadas. Sin kit aprobado, todo sigue como hoy. **Añade (pruebas de uso):** tamaño según la red, 9:16 en TikTok (B), y página y archivo por diapositiva, visibles en la ficha (K). **Añade (23 nov, decisión 23):** campo de sonido por publicación en el spec y en la ficha («sin sonido», «tema de Litoques en el vídeo», «elegir en la red: <tema>») |
| 4 | Skills en los encargos | `fase-13-skills` | `inputs.skills` por encargo, reglas fijas, `caption-writer` y `hook-writer` en los textos, y texto de las tareas programadas en `docs/tarea-programada-encargos.md` (sin tocar las tareas). **Añade (pruebas de uso):** ruta completa en `source_file` (A), encargo a Publer sin título ni nota interna (C), sin rechazos de limpieza masiva (I) y CTA de la marca en spec y textos (J). **Añade (revisión del 23 nov):** poner al día `docs/tarea-programada-encargos.md`: sigue diciendo «pendiente de aplicar» (versión 1.1.0, pasadas de día y de noche) y «cambios pendientes del 16 nov, sin aplicar» (`fondo_svg`, nota de material que falta), y no consta si las tareas recreadas el 22 nov llevan esos cambios (se mira con `list_triggers`, solo lectura). **Añade (decisión 23):** la instrucción de TikTok «no añadas música» pasa a depender del campo de sonido de la publicación |
| 5 | Vídeo con escenas | `fase-13-video` | Reels y clips a partir de escenas de plantilla (duración y transición por escena), montados con FFmpeg según `video-making`. Audio: tema de Litoques desde una carpeta de Drive o sin sonido y elegido en la red (decisión 21, a confirmar). **Añade (decisión 23):** comprobar, vía por vía, si se puede elegir sonido de la biblioteca de la red en carruseles de fotos y en reels. El disco ya aparece en las bibliotecas de Instagram y TikTok (decisión 26); queda por comprobar si la API oficial de Instagram y Publer permiten elegir el sonido; plan B: mezclar los MP3 en el vídeo |
| 7 | Arreglos de las pruebas de uso | `fase-13-arreglos` | Control de calidad que acepta `source_file` con añadidos (A), mensaje de márgenes por vía y manual corregido a 6 h (D), editor que no se cierra solo (E) ni queda en blanco al cambiar de filtro (F), «Enlace OK» y estado de las citas (G), contador de «Programación» (H), aviso de resultado que no llegó por una tarea cancelada (L) y `aiGenerated: true` en Publora para El DeLito; y el paso automático de «En producción» a «Borrador» cuando la publicación tiene piezas y texto (decisión 29) |
| 8 | Pendientes de siempre (antes «fase 14») | `fase-13-pendientes` | Ver la sección siguiente. El primer paso del bloque es preguntar a Raúl, de una en una, qué entra de lo que está «por decidir» |
| 6 | Cierre | `fase-13-cierre` | Pruebas (unitarias, e2e de la ficha del kit), manual y tutorial, estado, decisiones y copia al proyecto y al repositorio (`docs/fase-13.md`, `docs/estado.md`, `docs/decisiones.md`). Al terminar, y con el OK de Raúl, **actualizar y reactivar las dos tareas programadas** con el texto del bloque 4 (decisión 15) y pasar a la prueba de cero. **Añade (revisión del 23 nov):** el manual del proyecto (§12) aún dice que las dos tareas están borradas y que se recrearán en la prueba de cero (se recrearon el 22 nov), y dice 30 minutos de margen (arreglo D); además, las frases «(pendiente de confirmar)» del tutorial (ver 8.4) |

Orden: 1 (hecho), 2, 3, 4, 5, 7, 8 y, al final, el 6 (el cierre recoge también el 7 y el 8). Cada bloque pasa en GitHub antes de seguir y Raúl hace el merge. Si Raúl prefiere los arreglos antes (por ejemplo, para la «prueba de cero»), el bloque 7 no depende de los demás salvo A, que toca el control de calidad y el encargo de textos a la vez.

## Bloque 8 · Pendientes de siempre (lista heredada de la antigua fase 14)

Es una lista con su origen, no un plan: el plan (dudas, decisiones y orden) se hace al empezar el bloque. Se revisó el 22 nov, ~23:15, y otra vez el 23 nov, ~10:45, con todo el proyecto y el repositorio (ver «Revisión de pendientes» más abajo). Si sale algo nuevo, se apunta aquí con su origen.

**8.1 Apuntado por Raúl como pendiente (entran en el bloque)**

| # | Qué | Origen | Nota |
|---|---|---|---|
| 1 | Aviso de publicaciones a la misma hora | `estado.md`, `traspaso-pruebas.md`, `decisiones.md` | Hoy ya hay «posible duplicado» (mismo día, título y tipo, o misma hora y mismo texto) y «Mover a la siguiente hora libre» (choque de menos de 30 min). Hay que concretar qué falta |
| 2 | Lectura de Word y odt | Ídem | Hoy SocialHub lee `txt`, `md` y PDF para comprobar las citas. Pediría otra librería (OK de Raúl) |
| 3 | OCR de texto en imágenes | Ídem | Sin más detalle apuntado. Posible relación con que Claude no ve los PNG (hallazgo 4). Pediría otra librería (OK de Raúl) |

**8.2 Pendientes de Raúl (no son código)**

| # | Qué | Origen |
|---|---|---|
| 4 | Icono `.ico` propio del instalador | `estado.md`, `fase-12.md` |
| 5 | Permiso `instagram_manage_insights` de Meta (métricas de Instagram por la API) | `estado.md`, `fase-12.md` |
| 6 | Publicar una release (workflow Instaladores con «publicar» y una versión mayor que la instalada) para probar el aviso de versión nueva | `fase-12.md`, «Funciones finales» |
| 7 | **A confirmar con Raúl:** si ya regeneró la clave de usuario del sistema de Meta, que se recomendó regenerar porque pasó por el chat | `fase-12.md`, «Configuración de Meta» |
| 8 | **A confirmar con Raúl:** comparar el portátil con la torre HP con `ficha-equipo.bat` («otro día, a la torre le faltan cables»); ¿sigue vigente? | `fase-11.md`, «Pendiente» |

**8.3 Por decidir (ideas y límites anotados, sin decisión de Raúl)**

| # | Qué | Origen |
|---|---|---|
| 9 | Avisos fuera de la app (correo o móvil): hoy solo salen dentro de SocialHub | `fase-10.md`, `manual.md` |
| 10 | Meta Business Suite con Chrome como respaldo futuro de Instagram, y programar desde la web de Instagram (en pruebas de Meta desde agosto de 2026, solo algunas cuentas) | `fase-12.md`, estudio del bloque 4 |
| 11 | Mapa con más de 6 promociones: se recortan; haría falta un diseño nuevo (paginar o desplazar), «si llega a pasar» | `decisiones.md` |
| 12 | Pruebas de mutación (PIT): imponer el umbral del 75 % (variable `PIT_UMBRAL`), pendiente del OK de Raúl. La primera medición dio 26 % solo con pruebas unitarias | `decisiones.md` |
| 13 | Anti-SSRF de los enlaces: queda una ventana mínima de «DNS rebinding» (límite conocido; daño posible: lectura de 4 KB) | `decisiones.md` |
| 14 | Publora: historias de imagen por la API personal, sin comprobar con la cuenta real de Raúl (la documentación solo habla de vídeo en `videoType`) | `fase-7.md` |
| 15 | Una promoción dada de alta con inicio en una semana pasada no recibe propuestas de la semana en curso hasta el lunes a las 18:00 (para la siguiente) | `decisiones.md`, «Pendiente de Raúl, si quiere» |
| 16 | Tipos de valor anidados en puertos secundarios que asoman en puertos primarios (`InstagramAccountRepository.Account`, `PubloraAccountRepository.Account`, `MetricsRepository.Metrics`…): convendría sacarlos a tipos del dominio. No cambia el comportamiento | `decisiones.md`, arquitectura PR3 |
| 17 | TikTok por su API oficial (OAuth y auditoría de la app): descartado por ahora; reabrir si Raúl quiere probar la solicitud de auditoría. Publer Business (API de pago): solo si Raúl decide pagar | `decisiones.md`, estudio «otros sistemas» |
| 18 | OpenCode: «si se incorpora y con qué proveedor» (de la fase 0; hoy solo el script «Siempre abierto» lo mantiene abierto) | `fase-0.md` |
| 19 | Datos de ejemplo del prototipo: quitar Metricool (de la fase 0; puede estar ya hecho) | `fase-0.md` |
| 20 | Un Mac real de Raúl: FFmpeg y el `.dmg` solo se han probado en la máquina macOS de GitHub | `fase-12.md`, «Sin comprobar» |
| 25 | **Del borrador original (23 nov):** transcripción y subtítulos locales con Whisper.cpp (§9.3 y §10.1). Solo aparece en el borrador y en los datos de ejemplo del modo Demo; ningún documento de decisiones lo descarta ni lo apunta | `socialhub.txt` |
| 26 | **Del borrador original (23 nov):** presupuestos y cuotas de IA y paradas de seguridad (§8 y §13.4: presupuesto agotado, errores repetidos, credenciales caducadas…). Hoy hay un límite diario de encargos y reintentos con modelo más potente; nada más con ese nombre. Ningún documento de decisiones dice si se descarta (SocialHub no llama a la API de Claude, así que parte de §8 no aplica) | `socialhub.txt` |
| 27 | **Del borrador original (23 nov):** varias instalaciones sobre los mismos datos con exclusión para no publicar dos veces (§16.3). Está relacionado con pasar SocialHub a la torre HP (nº 8) | `socialhub.txt`, `memoria` de Raúl (portátil y torre) |

**8.4 Sin comprobar en real (se miran con las pruebas del lunes y la prueba de cero; no son código)**

- Una publicación real completa por la API oficial de Instagram (Prueba 1, lun 23 nov 15:00: **hecha, salió bien, sin música**) y por Publer en TikTok (Prueba 2, 19:00).
- Las tareas de Claude con las reglas nuevas (se recrearon el 22 nov y han corrido; la prueba de cero las valida con skills). No consta si llevan los cambios del 16 nov de `tarea-programada-encargos.md` (ver bloque 4).
- «Resumen de hoy» con datos reales (`fase-8.md`: «falta verla en pantalla con datos reales») y las recomendaciones de propuestas con Drive real (`fase-10.md`, «Sin comprobar en real»). Pueden haberse visto ya en las pruebas de uso; si no, se mira.
- Nombres de proceso de OpenCode en `mantener-abiertos.ps1` (`OpenCode`, `opencode`, `opencode-desktop`): sin comprobar en el PC de Raúl (`scripts/windows/README.md`).
- Frases «(pendiente de confirmar)» del `TUTORIAL.md`: ruta de la suspensión del PC en Windows, dónde se activan los conectores de Claude, detalle de objetivos, reglas, hitos y «Configuración» de la promoción, cómo se edita una tarea programada desde la app, cómo ver el límite de uso del plan, y si el resumen de la tarea del repositorio coincide con la vigente. Se cierran en la prueba de cero o en el cierre.

**8.5 Aparcado por decisión de Raúl (no entra; se puede reabrir)**

| # | Qué | Origen |
|---|---|---|
| 21 | Infografía y meme de texto para El DeLito (el contrato los admite, no se activan en el primer kit) | decisión 13 |
| 22 | Una cuarta fuente (titulares o manuscrita, por ejemplo Lora, Playfair Display, Caveat o Nunito) si hace falta | decisión 14 |
| 23 | Pinterest y YouTube (pines y miniaturas), cuando sean redes de SocialHub | decisión 2 |
| 24 | Subir portadas de reel a las redes | «Qué no entra» |

**Descartado:** Buffer, para siempre; lista de puesta en marcha con casillas; ideas de plantilla sin Claude; recogidas de Claude más frecuentes (`fase-10.md`); Lora (23 nov, Raúl se queda con las tres fuentes de `main`).

## Qué no entra

- Pinterest y YouTube (pines y miniaturas).
- Generación de imágenes con IA y que Claude entregue PNG o MP4.
- Subir portadas de reel a las redes.
- Cambios en la publicación (vías, márgenes). El bloque 7 solo cambia los mensajes de margen, no la lógica.
- Tocar las tareas programadas antes de tiempo: el texto queda listo en el bloque 4, pero las tareas se actualizan y reactivan al terminar la fase 13 (en el cierre), con el OK de Raúl (decisión 15).
- Infografía y meme de texto para El DeLito (decisión 13), de momento.
- Lo «por decidir» del bloque 8 (8.3) hasta que Raúl diga qué entra.

## Revisión de pendientes

**22 nov, ~23:15.** Raúl pidió confirmar que no existen más tareas pendientes. No se pudo confirmar: salieron pendientes que no estaban en ninguna lista (ahora en el bloque 8: 6, 7, 8, 15 a 20; y el arreglo L del bloque 7). Se leyeron entonces `traspaso-pruebas.md`, `fase-12.md`, `decisiones.md`, `fase-7.md`, `fase-8.md`, `fase-9.md`, `fase-11.md`, `como-trabajar.md` y los documentos de la fase 13 y 14.

**23 nov, ~10:45 (Raúl: «Revisa por favor toda la documentación actual para no olvidar nada»).** Se leyeron enteros todos los documentos del proyecto salvo los prototipos HTML: `fase-0.md`, `fase-2.md`, `fase-3.md`, `fase-4-5.md`, `fase-6.md`, `fase-7.md`, `fases-7-9.md`, `fase-8.md`, `fase-9.md`, `fase-10.md`, `fase-11.md`, `fase-12.md`, `decisiones.md`, `como-trabajar.md`, `manual.md`, `socialhub.txt` (borrador original, contrastado con lo construido), la nota «las bases de las socialhub…» y las notas de memoria del proyecto. Además se clonó el repositorio (solo lectura, `main` en la PR #81) y se leyeron `docs/estado.md`, `CLAUDE.md`, `docs/fase-13.md`, `docs/plantillas-svg.md`, la parte de la fase 13 de `docs/decisiones.md` y el principio de `docs/pruebas-flujos.md`; el resto del repositorio (`TUTORIAL.md`, `tarea-programada-encargos.md`, scripts, código) se revisó **solo con búsquedas de texto** («pendiente», «sin comprobar», «TODO», etc.), no leído entero. No se leyeron `propuestas/*.html`.

Qué salió:
1. **Contradicción con el repositorio, resuelta el 23 nov, ~10:50:** `main` ya tenía el bloque 1 con Crimson Text, Fira Sans y Bebas Neue y el plan decía «sin código» y «solo Lora». Raúl elige las tres de `main` (decisión 14); no se añade Lora.
2. **Copias desfasadas.** `docs/estado.md` del repositorio dice que las dos tareas están borradas y que el bloque 1 está «pendiente de revisión»; `docs/fase-13.md` del repositorio solo tiene las decisiones 1 a 9 (y «Bloque 1 programado»); el manual del proyecto (§12) dice que las tareas están borradas. Se corrige en el cierre (bloque 6) y al retomar la PR del plan.
3. **Tres ideas del borrador original sin decisión** (Whisper.cpp, presupuestos y paradas de seguridad, varias instalaciones): ahora en 8.3, números 25 a 27. El resto del borrador (mapa de fábrica con zoom, multimarca, Drive, local first con FFmpeg/Batik, estados de publicación y cadena de vías, calidad, copias, instalador de Windows) está construido o cambiado por decisión documentada: SocialHub no llama a la API de Claude (los encargos pasan por Drive) y no tiene orquestador multiproveedor. Fue un contraste de alto nivel, no punto por punto.
4. **`tarea-programada-encargos.md`** sigue con etiquetas «pendiente de aplicar» (bloque 4) y varias frases «(pendiente de confirmar)» del tutorial y nombres de proceso de OpenCode sin comprobar (8.4).
5. Del resto de las fases antiguas (0 a 10) no salió ningún pendiente nuevo que no estuviera ya en el bloque 8; lo que quedaba de la fase 0 (OpenCode, datos de ejemplo de Metricool) ya está en 8.3, números 18 y 19.

**Qué no se puede asegurar:** que no haya más pendientes en el código del repositorio ni en partes del repositorio no leídas (arriba), ni que la copia de `decisiones.md` del repositorio coincida con la del proyecto.

## Dudas resueltas y pendientes

Resueltas por Raúl el 22 nov: las decisiones 6 a 8 y las 11 a 22 de arriba. Resuelta el 23 nov: las fuentes (decisión 14, las tres de `main`). Orden fijado: pruebas de la fase 12 (en curso) → fase 13 (con bloque 8) → prueba de cero; las tareas se reactivan antes, al terminar la fase 13 (decisión 15). Resuelto el 23 nov (cierre del plan): decisiones 25 a 29; decisiones 30 y 31 (noche, tanda y perfiles); pendiente de Raúl: la 32.

Pendientes:
- **Audio y música (bloque 5 y campo de sonido del bloque 3):** confirmar qué permiten la API oficial y Publer respecto a elegir sonido en la red (carruseles de fotos y reels), y qué carpeta de Drive tendrá los temas de Litoques como alternativa (decisiones 21 y 23). El disco aparece en las dos redes (decisión 26).
- **`videoType: REELS` por defecto en Publora:** revisar en el bloque 7 si es correcto para carruseles e historias.
- **Bloque 8:** qué de 8.3 entra (incluidos 25 a 27), y qué falta exactamente en el aviso de misma hora (8.1). Se decide al empezar el bloque 8 (decisión 27).
- **Copia en el repositorio:** hecha el 23 nov, ~21:30, en la PR #82 (`fase-13-docs-al-dia`, pendiente del merge de Raúl): `docs/fase-13.md` (decisiones 1 a 32, arreglos, bloques 7 y 8, estado del bloque 1), `docs/estado.md` y las decisiones 10 a 32 en `docs/decisiones.md`. Queda copiar al proyecto de Claude `claude/decisiones.md` en el cierre.
