# SocialHub · Fase 6: producción editorial

Estado: **bloque 1 en `main`; bloque 2 hecho** (rama `fase-6/memoria`) · 3 nov 2026

## Respuestas de Raúl

| # | Tema | Decisión |
|---|---|---|
| 1 | Orden | Primero la identidad de marca y el control de calidad. Después, la memoria editorial y las propuestas |
| 2 | Identidad de la marca | Tono y público (con los temas que no se tocan), vocabulario (expresiones preferidas y prohibidas), estilo visual (colores, tipografía, logotipo, estilo fotográfico) y llamada a la acción con hashtags fijos. Se fija en la marca y las promociones la heredan |
| 3 | Si falla el control de calidad | La publicación no puede pasar a «Aprobada» ni a «Programada» mientras haya fallos, salvo que Raúl acepte cada aviso a mano. Queda anotado cuándo y por qué |
| 4 | Revisión con criterio | Claude la hace a petición: tipo de encargo nuevo «Revisión de calidad» (Haiku por defecto), con un botón en la publicación. Sus avisos se suman a los del código y cuenta en el límite diario |

## Bloques

1. **Identidad y control de calidad:**
   - Identidad de la marca, que va en cada encargo a Claude.
   - Comprobaciones automáticas de cada publicación.
   - Avisos que se aceptan a mano y bloqueo de «Aprobada» y «Programada».
   - Encargo «Revisión de calidad».
2. **Memoria editorial:** lo publicado, lo rechazado y por qué, y las correcciones de Raúl. Va en los encargos.
3. **Propuestas de contenido y calendario editorial:** Claude propone y Raúl aprueba o rechaza con el motivo.

## Comprobaciones automáticas (bloque 1)

Límites de las plataformas, según su documentación (nov 2026):

| | Instagram | TikTok |
|---|---|---|
| Texto | 2.200 caracteres, 30 hashtags | 2.200 caracteres (vídeo) |
| Imagen | JPEG, 8 MB, proporción de 4:5 a 1,91:1 (historias 9:16) | JPEG, PNG o WebP, 20 MB, 9:16 recomendado |
| Carrusel | 20 elementos | 35 fotos |
| Vídeo | Reel: MP4/MOV, 300 MB · historia: 100 MB | MP4, MOV o WebM, 4 GB |

Fuentes: [Instagram Graph API · IG User Media](https://developers.facebook.com/documentation/instagram-platform/instagram-graph-api/reference/ig-user/media) y [TikTok media guidelines (Late)](https://docs.getlate.dev/resources/media-guidelines/tiktok).

Qué se comprueba:
- **Texto:**
  - Que lo haya y que no pase del máximo de la plataforma, contando también los hashtags de Instagram.
  - Que no lleve expresiones prohibidas; se comparan sin mayúsculas ni tildes.
  - Que estén la llamada a la acción (salvo en historias) y los hashtags fijos.
  - Que no repita el de otra publicación de la promoción.
- **Archivos:**
  - Que tenga al menos uno, que existan y que su formato y tamaño valgan para la plataforma y el tipo.
  - Proporción de las imágenes y número de elementos del carrusel.

## Bloque 1 · hecho

- **Identidad:**
  - `GET/PUT /api/brands/{id}/identity`. Se edita en «Marcas», al abrir una marca.
  - Va en cada encargo a Claude como `inputs.brand_identity`, solo con los campos rellenos.
- **Control de calidad:**
  - `GET /api/publications/{id}/quality` devuelve los avisos del código (calculados en el momento) y los de la última revisión de Claude.
  - `POST /api/publications/{id}/quality-acceptances` acepta un aviso con su motivo.
  - Cada aviso tiene una clave estable (`TEXT_MISSING`, `FORBIDDEN:compra ya`, `FILE_SIZE:<ruta>`…), así que una aceptación sigue valiendo mientras el aviso no cambie.
- **Bloqueo:**
  - Con avisos abiertos, el paso a «Aprobada» o «Programada» responde 409 con cuántos quedan.
  - Al aprobar la revisión, las publicaciones con avisos siguen en revisión con la marca «Control de calidad pendiente».
- **Tipo de pieza:** se deduce del tipo libre de la publicación: carrusel, historia, reel, vídeo o clip y, si no, publicación. Las historias no necesitan texto, llamada a la acción ni hashtags fijos.
- **Revisión de calidad con Claude:**
  - Encargo `QUALITY_REVIEW`, Haiku por defecto. Lleva la publicación, sus archivos y los avisos del código.
  - Claude devuelve `outputs.issues: [{message}]`. Cada revisión sustituye a la anterior.
  - Botón «Revisar con Claude» en el editor de cada publicación, junto al panel de calidad.
- **Pruebas:** 79 de backend y 19 de frontal.

## Bloque 2 · memoria editorial (respuestas de Raúl)

| # | Tema | Decisión |
|---|---|---|
| 1 | Qué se recuerda | Lo publicado, lo rechazado y por qué, las correcciones de Raúl a los textos de Claude y las decisiones editoriales que escribe Raúl por marca |
| 2 | Cuánto va a Claude | Lo publicado de las últimas 8 semanas, los últimos 20 rechazos y las últimas 20 correcciones, y todas las decisiones. Solo de la misma marca |
| 3 | Motivo de un rechazo | Motivo fijo (tono, repetido, fuera de la marca, error de datos, formato u otro) más una nota opcional. Es obligatorio al cancelar una publicación o al descartar un texto de Claude |

Cómo se aplica:
- **Rechazos:**
  - Al cancelar una publicación se pide el motivo, y queda con su título y su texto.
  - «Descartar texto» en el editor borra el texto y los hashtags que propuso Claude y guarda el motivo y lo descartado.
- **Correcciones:** SocialHub guarda la última propuesta de Claude en cada publicación. Si Raúl guarda un texto distinto, se apunta el antes y el después.
- **Decisiones editoriales:** notas por marca que Raúl añade o borra en «Marcas».
- **En los encargos:** todo va en `inputs.editorial_memory`.

### Bloque 2 · hecho

- **API:**
  - `GET/POST /api/brands/{id}/decisions` y `DELETE /api/brands/{id}/decisions/{decisión}`.
  - `GET /api/brands/{id}/editorial-memory`.
  - `POST /api/publications/{id}/text-rejections`.
  - `status-changes` admite `reason` y `note`; `reason` es obligatorio para cancelar.
- **Correcciones:** cuando Claude deja un texto, se guarda como su propuesta. La primera vez que Raúl lo cambia y guarda, se apunta la corrección y se olvida la propuesta, así que los cambios siguientes no cuentan como correcciones de Claude.
- **En los encargos:** `inputs.editorial_memory` lleva:
  - las decisiones;
  - lo aprobado, programado o publicado de las últimas 8 semanas (título, tipo, fecha y texto recortado);
  - los últimos 20 rechazos y las últimas 20 correcciones, solo de la misma marca.
- **Interfaz:**
  - En el editor de la publicación, «Pasar a Cancelada» y «Descartar texto» piden el motivo.
  - En «Marcas», la tarjeta «Memoria editorial» muestra y edita las decisiones y enseña los rechazos y correcciones.
- **Pruebas:** 80 de backend y 21 de frontal.

