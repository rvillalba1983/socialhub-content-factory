# Fase 12 · Todo lo pendiente (una sola fase)

Estado: **plan propuesto el 11 nov 2026, pendiente del OK de Raúl.** No se programa nada hasta entonces.

Raúl pidió seguir sin él y revisar todo al final con pruebas funcionales completas. Sobre SocialHub 0.9.3 no contestó qué había visto: se toma «nada».

## Orden propuesto de los bloques

Una rama y una PR por bloque.

| # | Bloque | Rama | Por qué en este orden |
|---|---|---|---|
| 1 | Detalles visuales de «Necesita atención» y «Qué hacer ahora» | `fase-12-visual` | Pequeño, solo frontal y probable con Playwright. Se ve en el día a día |
| 2 | TikTok en SocialHub | `fase-12-tiktok` | Casi todo es configuración de Raúl. Desbloquea publicar en las dos redes |
| 3 | Métricas reales en la pestaña de la promoción | `fase-12-metricas` | Lo más grande. Necesita datos de las publicaciones de 1 y 2 |
| 4 | API oficial de Instagram y estudio de Chrome (web de Instagram / Meta Business Suite) | `fase-12-instagram` | Depende de que Raúl cree una app de Meta. El estudio puede ir antes como documento |
| 5 | Instalador de macOS (`.dmg`) y sus pruebas | `fase-12-macos` | Sin un Mac para probar, es lo de menos uso. Gasta 10 veces más minutos de GitHub |

## Lo que hay hoy (leído en el código)

- **Métricas por publicación:** 6 cifras (reproducciones, alcance, me gusta, comentarios, guardados, compartidos, más una nota) en `publication_metric` (`V20`). Las lee Claude con Chrome en el PC de Raúl (encargo «Leer métricas»), a los **7 días**, hasta 10 publicaciones por encargo y 3 intentos.
- **Pestaña «Métricas» de la promoción** (`ControlCenter.tsx`): 8 etiquetas fijas que dicen siempre «Sin dato». Sin backend. Impresiones, interacciones, seguidores ganados y clics en el enlace no se leen en ningún sitio.
- **Publicación:** Publer (por Chrome) y Publora. La «API oficial» es un hueco «no configurado» (`UnconfiguredRoutes`).
- **TikTok y marcas:** Publora guarda una cuenta por marca y red, sin impedir que dos marcas usen la misma. Publer identifica la marca por su `handle`.
- **«Necesita atención»:** tarjeta absoluta dentro del mapa (`World.tsx`, máximo 2 avisos). Los textos se recortan por líneas (`-webkit-line-clamp`: 1 en el título, 3 en el resto).
- **«Qué hacer ahora»:** `Recommendations.tsx`, 3 visibles y «Ver N más». La franja tiene `max-height: 45vh` con scroll.
- **`.dmg`:** el job `macos` de `instaladores.yml` existe pero no se ha lanzado nunca. PostgreSQL y FFmpeg van dentro del jar **solo en su versión de Windows**, así que en macOS no arrancaría tal cual.

## Lo que se ha investigado fuera

- **API oficial de Instagram** ([Meta · Content Publishing](https://developers.facebook.com/documentation/instagram-platform/content-publishing)):
  - Cuenta profesional; app de Meta con «Business Login for Instagram» (no hace falta página de Facebook).
  - Hasta 100 publicaciones por API cada 24 h. Imágenes (solo JPEG), carruseles de hasta 20, reels e historias.
  - **Sigue sin programar:** se publica al llamar. SocialHub tendría que estar abierto a la hora; desde la fase 11 lo mantiene abierto «Siempre abierto».
  - **Vídeos y reels pueden subirse desde el PC** (subida por partes a `rupload.facebook.com`). Las **imágenes** siguen necesitando una URL pública.
- **Meta Business Suite** (gratis, en el navegador): programa reels y publicaciones hasta 90 días antes. Exige que la cuenta de Instagram esté vinculada a una página de Facebook.
- **Web de Instagram:** desde agosto de 2026 Instagram está probando programar publicaciones y reels desde instagram.com (hasta un mes antes), pero solo en algunas cuentas. Las historias no se pueden programar ni en la app ni en la web.
- **TikTok en Publer:** admite cuentas personales y de empresa. Vídeos de 3 s a 10 min y hasta 1 GB, y carruseles de fotos. Privacidad y permisos (comentarios, dúos, stitch) se eligen en cada vídeo; los sonidos tienen que ir ya en el vídeo. Una cuenta de empresa de TikTok solo puede usar la biblioteca de música comercial.

## Respuestas de Raúl

- 11 nov: 0 → orden de la tabla · 1 → (a) · 2 → (a) · 3 → (a).
- 4 → cuenta personal (la de empresa exige demostrar que es una empresa real) · 5 → (b) públicas desde la primera · 6 → (b) sin aviso ni separación entre marcas.
- 7 → Claude lo miró en Publer: plan **Free**, con Instagram de El DeLito y Litoques conectados. Admite 3 cuentas (TikTok cabe justo) y 10 publicaciones programadas por cuenta a la vez, así que TikTok tendrá como mucho 10 programadas entre las dos marcas.
- 8 → (a) · 9 → (a) · 10 → (a) · 11 → (b) solo a los 7 días · 12 → (a) · 13 → (a).

## Dudas para Raúl (de una en una, la recomendada primero)

### General
0. **Orden de los bloques:** el de la tabla de arriba / otro.

### Bloque 1 · Visual
1. **Con 3 avisos o más,** que la tarjeta tape la estación 07: (a) **la tarjeta crece hacia arriba y como mucho enseña 2 avisos, con «Ver N más»** que abre la lista entera / (b) moverla fuera del mapa, junto a la franja.
2. **Textos cortados con «…»:** (a) **dos líneas en el título y el texto completo al pasar el ratón o tocar** / (b) siempre el texto completo, aunque la tarjeta crezca.
3. **«Ver N más» suelto en la franja:** (a) **que quede pegado a la última fila visible, dentro de la franja** / (b) quitarlo y enseñar todas con scroll.

### Bloque 2 · TikTok
4. **¿Cuenta personal o de empresa?** (a) **seguir como personal**: Publer la admite y conserva toda la música / (b) pasarla a empresa (estadísticas en la web, pero solo música comercial). En estado.md estaba previsto (b).
5. **Primeras publicaciones de TikTok:** la decisión inicial era «privadas, solo para la cuenta». (a) **la primera de cada marca privada y, si sale bien, públicas** / (b) públicas desde el principio.
6. **En SocialHub,** las dos marcas con la misma cuenta: (a) **avisar en la ficha de la marca que la cuenta es compartida y no programar dos TikTok de marcas distintas a menos de X horas** / (b) sin aviso ni límite.
7. **Plan de Publer:** ¿cuántas cuentas te deja tu plan (las de Instagram de las dos marcas más TikTok)? Si no cabe, se verá antes de conectar nada.

### Bloque 3 · Métricas (las 5 de estado.md)
8. **Qué cifras:** (a) **las 6 que ya se leen por publicación, sumadas por promoción, y quitar de la pestaña las 4 que no se leen** / (b) leer también impresiones, seguidores ganados y clics (son de la cuenta, no de la publicación; más trabajo para Claude con Chrome).
9. **Cómo resumirlas:** (a) **totales de la promoción y comparación por formato y por red** / (b) además por día de la semana y hora / (c) solo totales y medias.
10. **Muestra mínima para sacar conclusiones:** (a) **5 publicaciones por grupo; con menos se enseña la cifra pero «pocos datos»** / (b) 3 / (c) 10.
11. **Cuándo medir:** (a) **a las 48 h y a los 7 días** (se ve el arranque y el total) / (b) solo a los 7 días, como ahora (la mitad de encargos).
12. **Instagram y TikTok:** (a) **por separado, con una fila de total** / (b) juntos.
13. **De dónde salen:** (a) **seguir con Claude y Chrome** (gratis, ya funciona) / (b) estadísticas de Publora o de la API oficial cuando estén (sin encargos, pero dependen del bloque 4 o de Publora).

### Bloque 4 · Instagram
14. **Vía de publicación:** (a) **API oficial solo para reels y vídeos (se suben desde el PC) y Publer sigue para imágenes y carruseles** / (b) API oficial para todo, subiendo las imágenes a una URL pública (habría que elegir dónde: Drive no sirve tal cual) / (c) solo el estudio, sin programar la API.
15. **Sustituir a Publer:** propuesta del estudio: (a) **Meta Business Suite con Chrome como vía de respaldo** (gratis, programa hasta 90 días, necesita página de Facebook vinculada) y esperar a que la programación en instagram.com llegue a tus cuentas / (b) cambiar ya Publer por Business Suite / (c) seguir solo con Publer.
16. **App de Meta:** la tienes que crear tú en developers.facebook.com (yo no creo cuentas ni inicio sesión). Te dejaría los pasos. ¿Te parece bien?

### Bloque 5 · macOS
17. **¿Tienes un Mac** para probar el `.dmg`? (a) **no: se genera y se prueba solo en la máquina macOS de GitHub (instalar, arrancar y abrir la web)** / (b) sí, lo pruebas tú.
18. **PostgreSQL y FFmpeg para macOS:** (a) **meterlos en el `.dmg` con la versión de macOS (Apple Silicon)**. Hay que añadir sus binarios: pide tu OK como librería nueva / (b) pedir al usuario que los instale con Homebrew.

## Cómo sabremos que está bien

Se concreta por bloque al aprobar el plan. Como mínimo: cada PR pasa en GitHub (backend en Windows; frontal en Linux; el `.dmg` en macOS solo en el bloque 5), y lo visual se comprueba con Playwright en modo real sin errores en la consola. Al final, Raúl hace las pruebas funcionales completas.
