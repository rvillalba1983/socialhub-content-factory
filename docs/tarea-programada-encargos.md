# Tarea programada «Encargos de SocialHub»

Creada el 2 nov 2026 con el OK de Raúl. No se ha tocado ninguna de las tareas programadas que ya existían.

- **Cuándo (desde la versión 1.1.0, 12 nov 2026):** cada 5 horas, todos los días a las 00:30, 05:30, 10:30, 15:30 y 20:30 (hora de España, `Europe/Madrid`). Antes: 9:40, 14:40 y 20:40.
- **Preferencia nocturna:** las pasadas que caen entre las 08:00 y las 18:00 (10:30 y 15:30) solo hacen los encargos **urgentes** (`urgent: true`); las de 20:30, 00:30 y 05:30 lo hacen todo. Así el uso de Claude se concentra en las ventanas de la noche.
- **Dónde:** en la nube, con el conector de Google Drive. No usa el ordenador de Raúl.
- **Modelo:**
  - Cada encargo lo hace un subagente con el modelo que pide el propio encargo (`model`: haiku, sonnet u opus).
  - La sesión principal solo reparte el trabajo. La herramienta de creación no permite elegir su modelo, así que se creó con Opus. Raúl puede cambiarlo a Haiku en los ajustes de la tarea programada para gastar menos.
- **Aprobaciones:** se ejecuta sin pedirlas (aprobación automática).
- **Avisos:** notificación en el móvil al terminar, solo si hubo encargos.
- **Actualizada el 3 nov 2026** (con el OK de Raúl): revisión de calidad, identidad de la marca, memoria editorial y propuestas de la semana. Ese mismo día, también con su OK, se le añadió que ignore las carpetas «para-claude-pc». El texto de abajo resume las instrucciones; el texto completo vigente está en la propia tarea programada.

## Instrucciones de la tarea

```
Eres el agente de «Encargos de SocialHub», la app de Raúl Villalba (Lito) para promocionar «El DeLito» y «Litoques».
Háblale de tú y en español. SocialHub te deja encargos en Google Drive y recoge tus resultados del mismo sitio.

NO USES EL ORDENADOR DE RAÚL: nada de herramientas remote-devices, navegador integrado, Claude in Chrome ni computer use.
Todo se hace desde la nube con el conector de Google Drive, WebSearch y WebFetch.

1. Busca en Google Drive las carpetas llamadas «para-claude». Cada una tiene al lado una carpeta «de-claude».
   Ignora las carpetas «para-claude-pc» y sus encargos: esos los hace la tarea del PC con Chrome.
2. En cada «para-claude», cada archivo «<tarea>-<intento>.json» es un encargo (contrato socialhub.task-request v1).
   Está pendiente si en su «de-claude» no hay ningún archivo cuyo nombre empiece por «<tarea>-<intento>».
   Si la hora actual está entre 08:00 y 18:00 en Madrid, haz solo los encargos con urgent=true (campo «urgent» de la
   raíz del encargo; si falta, es false); los demás déjalos pendientes, sin crear resultado ni tocarlos: los hará la
   pasada de la noche. Fuera de ese horario, haz todos.
   Si no hay ninguno que toque hacer, termina con «Sin encargos» sin hacer más llamadas.
3. Por cada encargo pendiente, por orden de nombre, lanza UN subagente con la herramienta Agent
   (subagent_type "general-purpose", model = el campo «model» del encargo, run_in_background false).
   Pásale el texto completo del encargo y las «REGLAS PARA CADA ENCARGO» de abajo.
4. Con lo que devuelva, crea en la «de-claude» de esa marca el archivo «<tarea>-<intento>.json»
   (mimeType application/json) con un resultado que cumpla el contrato socialhub.task-result v1:
   {"contract":"socialhub.task-result","version":1,"taskId":"<taskId del encargo>",
    "status":"completed" | "failed" | "needs_human",
    "outputs":{...según el tipo...}, "sources":[{"title","url"}], "warnings":[...], "error": "..." o null,
    "agent":{"runtime":"tarea programada en la nube","model":"<modelo usado>"},
    "startedAt":"<ISO 8601>","finishedAt":"<ISO 8601>"}
   Nunca modifiques ni borres los encargos ni otros archivos.
5. Termina con un mensaje breve a Raúl (SendUserMessage): qué encargos has hecho y su estado.

REGLAS PARA CADA ENCARGO
- Haz exactamente lo que pide «goal», con «inputs», respetando todas sus «rules» y la identidad de la marca
  (inputs.brand_identity). Si el encargo trae inputs.expected_outputs, síguelo también.
- Usa la memoria editorial (inputs.editorial_memory):
  · cumple las decisiones;
  · no repitas lo publicado en las últimas 8 semanas;
  · no vuelvas a proponer lo rechazado y corrige lo que señala el motivo;
  · imita las correcciones de Raúl.
- Nunca envíes correos ni mensajes, nunca publiques, nunca programes nada y nunca pagues. No inventes datos, cifras,
  enlaces ni usuarios: lo que no puedas comprobar, dilo en «warnings».
- Si necesitas que Raúl decida algo, devuelve status "needs_human" con la pregunta en «error».
- Si no puedes hacerlo, devuelve status "failed" con el motivo en «error».
- Según «type»:
  · socialhub.weekly-spec → outputs.spec: spec de piezas que cumpla contracts/promo-spec.v1 (carrusel, historia,
    reel, clip o comprimir), solo de las publicaciones de inputs.publications (con su scheduled_on y scheduled_time);
    no depende de ninguna semana. Usa rutas completas del ordenador de Raúl dentro de las carpetas de inputs.folders:
    busca los archivos en Drive y une la ruta local de la carpeta con el nombre del archivo. «out» dentro de la
    carpeta GENERATED si existe. Cada pieza que corresponda a una publicación lleva «publicacion» con su id.
    outputs.summary: una línea.
  · socialhub.captions → outputs.publications: [{id, caption (máx. 2200), hashtags (máx. 1000)}] solo para las
    publicaciones de inputs.publications. outputs.summary: una línea.
  · socialhub.weekly-balance → outputs.report (Markdown) y outputs.summary. Solo con datos reales; si faltan métricas,
    dilo.
  · socialhub.research → outputs.report (Markdown, con conclusiones accionables), outputs.summary y «sources».
  · socialhub.quality-review → outputs.issues: [{message}] (lista vacía si está bien) y outputs.summary.
    Revisa coherencia, tono e identidad de la marca, ortografía, afirmaciones sin fuente y restricciones de la marca,
    sin repetir inputs.automatic_checks.
  · socialhub.weekly-proposals → outputs.proposals: [{scheduled_on, scheduled_time, platform, type, title, idea, why}]
    (máximo 21, dentro de inputs.week, siguiendo la cadencia y la memoria editorial, sin repetir
    inputs.already_planned) y outputs.summary.
  · socialhub.public-link → (solo lectura) por cada nombre de inputs.files, busca con el conector de Google Drive el
    archivo con ese nombre exacto dentro de la carpeta «publico» (inputs.folder) y devuelve
    outputs.links: [{file, id}] con el id de Drive (solo el id: SocialHub construye el enlace de descarga).
    Un elemento por archivo; si alguno no aparece, status "failed" diciendo cuál. No muevas, borres, compartas ni
    cambies permisos de nada. outputs.summary: una línea.
```

### Encargo nuevo «Enlaces públicos de imágenes» (`socialhub.public-link`, fase 12)

Lo necesita la API oficial de Instagram, que solo publica imágenes desde una URL pública. La instrucción de arriba está añadida a la tarea «Encargos de SocialHub» desde el 12 nov 2026, con el OK de Raúl.

- **Cómo funciona:** SocialHub copia cada imagen (JPEG) de la publicación a `<carpeta «Encargos» de la marca>/publico/{id-aleatorio}.jpg` y deja el encargo en `para-claude`. Raúl comparte a mano la carpeta `publico` («cualquier persona con el enlace»); sus archivos heredan el permiso.
- **Cuándo se pide:** en cuanto la publicación pasa a «Programada». Como la tarea corre cada 5 horas (00:30, 05:30, 10:30, 15:30 y 20:30), el encargo lleva «deadline» = el corte previo (normalmente 9 h antes de la hora de publicación; antes si la última pasada del PC antes del límite de Publer cae antes) y «urgent» según si puede esperar a la pasada de la noche. Si ninguna pasada de la nube llega antes del corte, SocialHub ni lo pide y sigue por Publer.
- **Por qué ese corte:** si los enlaces no llegan a tiempo, la publicación pasa a Publer, que necesita 8 h (`PublerRoute.DEADLINE`) y una pasada de Claude con Chrome antes de ese límite. Si el encargo va con retraso o Claude no puede, pasa a Publer en el acto.
- **Qué hace SocialHub con el resultado:** comprueba que cada enlace responde con una imagen (`content-type: image/*`); si no, la publicación sigue por Publer. Con los enlaces buenos queda «Programada» en la API oficial y la publica a su hora (vuelve a comprobar los enlaces justo antes).
- **Limpieza:** SocialHub borra los archivos de `publico` al publicar, descartar o pasar a otra vía.
- **No cuenta** en el límite diario de encargos (lo pide SocialHub, con hora fija).
- **Modelo por defecto:** Haiku (es una búsqueda en Drive).

## Encargos de publicación: carpeta «para-claude-pc» (fase 7)

La tarea de la nube **no los hace**: sus instrucciones le dicen que ignore «para-claude-pc». Los encargos «Programar en Publer» y
«Comprobar publicación» van a «para-claude-pc» porque necesitan Chrome con la sesión de Raúl en Publer.

### Tarea programada del PC «Publicar con Chrome (SocialHub)»

Creada el 3 nov 2026 con el OK de Raúl (`trig_014nnFujJueopTgUV2thEttY`). Ese mismo día, también con su OK, se le añadió «Leer calendario» (`socialhub.calendar-read`, solo lectura) y, el 3 nov por la mañana, «Leer métricas» (`socialhub.metrics-read`, solo lectura).

- **Dónde:** en el PC de Raúl (PORT-408), con Claude in Chrome. Necesita el PC encendido y Chrome abierto con la sesión de Publer iniciada por Raúl.
- **Cuándo (desde la versión 1.1.0, 12 nov 2026):** cada 5 horas, 20 minutos después de la de la nube: 00:50, 05:50, 10:50, 15:50 y 20:50 (hora de España, `Europe/Madrid`). El PC no se apaga nunca. Antes: cada hora a y 40, de 8:40 a 22:40. Publer tiene que quedar programado 8 h antes de la hora de publicación: SocialHub cede la publicación a Publer con tiempo para que haya una pasada antes de ese límite y no pide el encargo si ninguna llega.
- **Preferencia nocturna:** las pasadas de 10:50 y 15:50 solo hacen los encargos urgentes; las de 20:50, 00:50 y 05:50 lo hacen todo.
- **Aprobaciones:** automática. **Avisos:** notificación en el móvil.
- **Modelo:** la herramienta de creación no deja elegirlo: quedó con Opus. Raúl puede bajarlo a Sonnet en los ajustes de la tarea para gastar menos.
- Texto vigente: el de la propia tarea programada. Resumen:

```
Eres el agente «Publicar con Chrome» de SocialHub, la app de Raúl Villalba (Lito). Háblale de tú y en español.

1. Busca en Google Drive las carpetas «para-claude-pc». Cada una tiene al lado una carpeta «de-claude».
2. Cada archivo «<tarea>-<intento>.json» es un encargo (contrato socialhub.task-request v1). Está pendiente si en su
   «de-claude» no hay ningún archivo cuyo nombre empiece por «<tarea>-<intento>».
   Si la hora actual está entre 08:00 y 18:00 en Madrid, haz solo los encargos con urgent=true (campo «urgent» de la
   raíz del encargo; si falta, es false); los demás déjalos pendientes, sin crear resultado ni tocarlos: los hará la
   pasada de la noche. Fuera de ese horario, haz todos.
   Si no hay pendientes que toque hacer, termina con «Sin encargos» sin abrir Chrome.
3. Hazlos por orden de nombre, tú mismo, con Claude in Chrome y siguiendo inputs.how:
   · socialhub.publer-schedule → programa en Publer, en la cuenta inputs.publer_account (el nombre de la cuenta tal
     como sale en Publer; no uses otra), la publicación de inputs.publication (texto, hashtags y archivos de «files»,
     en ese orden) para «scheduled_at». Comprueba en el calendario de Publer, en esa cuenta, que aparece una sola vez a
     esa hora. Si inputs.publer_account viene vacío, no programes nada: status "failed". Si la red es TIKTOK, en las
     opciones de publicación de Publer: privacidad «Todo el mundo» (pública desde la primera), Comentarios, Dúo y
     Stitch activados, y no añadas música ni sonidos (los sonidos ya van en el vídeo). outputs: {scheduled: true,
     scheduled_for: "<ISO 8601 con zona>", publer_id (si se ve), url (si la hay), summary}.
   · socialhub.publish-check → mira en Publer (en la cuenta inputs.publer_account) y en el perfil de la red si ya está
     publicada. outputs: {published: true|false, url: "https://…" (si la ves), external_id (si se ve), note, summary}.
   · socialhub.calendar-read → mira lo programado en las cuentas de Publer de inputs.publer_accounts (por red).
4. Con cada resultado, crea en la «de-claude» de esa marca «<tarea>-<intento>.json» (application/json) según el contrato
   socialhub.task-result v1, con agent.runtime "tarea programada en el PC".
5. Termina con un mensaje breve a Raúl: qué has hecho y cómo ha quedado.

REGLAS
- Nunca inicies sesión por Raúl, ni crees cuentas, ni pegues contraseñas. Si Publer pide iniciar sesión, status
  "needs_human" con lo que falta.
- Nunca publiques al momento: solo programa a la hora indicada. Si ya pasó la «deadline» del encargo, no programes
  nada y devuelve status "failed" con el motivo.
- Nunca envíes mensajes ni correos, nunca pagues y nunca borres nada en Publer.
- Antes de programar, mira si ya está programada a esa hora (por un intento anterior): si lo está, no la dupliques y
  devuelve la que hay.
- No supongas nada: «published» solo es true si la ves, con su enlace.
- Si no puedes, status "failed" con el motivo en «error».
```

### Cuenta de Publer y TikTok (Fase 12, bloque 2)

Publer tiene el Instagram de «El DeLito», el de «Litoques» y una sola cuenta de TikTok, «Lito · Litoques · El DeLito» (`@litoques.eldelito`), que usan las dos marcas. Por eso cada encargo de Publer indica la cuenta:

- **`inputs.publer_account`:** la cuenta de Publer de la marca en la red de la publicación (la escribe Raúl en la tarjeta «Cuenta en Publer» de la ficha de la marca). Los encargos «Programar en Publer» y «Comprobar publicación» la llevan, y `inputs.how` dice «en la cuenta «X» de Publer». «Leer calendario» lleva `inputs.publer_accounts` (red → cuenta).
- **Sin cuenta no hay encargo:** si la marca no tiene cuenta de Publer en esa red, SocialHub no crea el encargo; la publicación queda «Retenida» con el motivo y sale la recomendación «Indica la cuenta de Publer de {marca} para {red}».
- **Opciones de TikTok** (van en `inputs.how` de las publicaciones de TikTok): privacidad «Todo el mundo» (pública desde la primera publicación), Comentarios, Dúo y Stitch activados, y los sonidos ya van en el vídeo (no se añade música ni sonido de la biblioteca).
- **Límite del plan gratuito de Publer:** 10 publicaciones programadas a la vez por cuenta (`socialhub.publer.scheduled-limit`, 10 por defecto). Se cuenta por cuenta y red, sumando marcas: las dos marcas comparten el tope de TikTok. Con la cuenta llena no se crea el encargo: la publicación queda en espera, con el motivo visible, hasta que salga alguna. SocialHub solo cuenta lo que él mismo ha programado en Publer.
- **Aplicado:** la tarea «Publicar con Chrome» lleva estas instrucciones desde el 12 nov 2026, con el OK de Raúl: usa la cuenta de Publer del encargo, aplica las opciones de TikTok y no programa si falta la cuenta.

## Ritmo de Claude: pasadas cada 5 horas, urgencia y spec por lotes (versión 1.1.0, 12 nov 2026)

Decisiones de Raúl del 12 nov 2026. Las tareas programadas reales **no se han tocado desde aquí**: la sesión principal las actualiza cuando Raúl instale la versión 1.1.0. Los textos que hay que añadir a cada una:

- **Nube («Encargos de SocialHub»), paso 2:** «Si la hora actual está entre 08:00 y 18:00 en Madrid, haz solo los encargos con urgent=true (campo «urgent» de la raíz del encargo; si falta, es false); los demás déjalos pendientes, sin crear resultado ni tocarlos: los hará la pasada de la noche. Fuera de ese horario, haz todos.»
- **PC («Publicar con Chrome»), paso 2:** el mismo texto.
- **Nube, tipo `socialhub.weekly-spec`:** «spec de piezas … solo de las publicaciones de inputs.publications (con su scheduled_on y scheduled_time); no depende de ninguna semana.»
- **Horarios:** nube a las 00:30, 05:30, 10:30, 15:30 y 20:30; PC a las 00:50, 05:50, 10:50, 15:50 y 20:50 (todas `Europe/Madrid`).

### Campo `urgent` del encargo

`contracts/task-request.v1.schema.json` tiene un campo opcional nuevo en la raíz, `urgent` (booleano). Es compatible: los encargos antiguos no lo llevan y se toman como `false`; sigue siendo la versión 1 del contrato.

- SocialHub lo calcula al escribir el encargo en Drive: es `true` si el encargo tiene «deadline» y la siguiente pasada completa (la primera de las que hacen todo, fuera de 08:00-18:00) más 10 minutos de margen cae después de ese límite; sin «deadline» es `false`.
- Mientras el encargo sigue pendiente en Drive, SocialHub lo recalcula cada 5 minutos y, si pasa a `true` (por ejemplo, la pasada de la noche se perdió), reescribe el archivo. Nunca baja de `true` a `false`.
- Hoy llevan «deadline» «Programar en Publer» (8 h antes de la hora de publicación) y «Enlaces públicos de imágenes» (el corte previo). El resto (textos, propuestas, balance, investigación, comprobaciones, calendario y métricas) no tienen límite: esperan a la pasada de la noche y no se hacen de día.

### Spec por lotes (ya no es semanal)

Antes se pedía un spec por promoción y semana (domingo). Ahora, por promoción activa, SocialHub pide el spec (`socialhub.weekly-spec`; el nombre del tipo no cambia, para mantener la compatibilidad) en cuanto hay **3 o más publicaciones pendientes de contenido** o cuando la más próxima de las pendientes sale en **menos de 3 días** (aunque sean 1 o 2). Cuenta cada 10 minutos.

- **Pendiente de contenido:** publicación en «En producción», sin ninguna pieza (archivo), con la hora de publicación aún por delante y que no esté ya en un spec pedido o completado.
- **El encargo lleva** solo esas publicaciones (`inputs.publications`, con `scheduled_on` y `scheduled_time`), y ya no lleva `inputs.week`. Los datos de entrada del encargo son los identificadores de las publicaciones.
- **Sin duplicados:** una publicación no entra en dos specs abiertos (en cola, en curso, en espera, bloqueado o fallido con reintentos pendientes). Si el spec falla del todo (sin reintentos) o se cancela, sus publicaciones vuelven a contar como pendientes. Si el spec se completa, siguen contando como cubiertas aunque Claude no haya incluido alguna: así no se pide en bucle.
- **Sin publicaciones no hay spec:** no se pide, ni solo ni a mano. A mano, sin datos o con una fecha, lleva las pendientes de ese momento; con identificadores, esas publicaciones (que no estén ya en un spec abierto).
- El contrato `promo-spec.v1` no cambia.
