# SocialHub — Fases 4 y 5: Google Drive y orquestador de IA

Estado: **plan aprobado por respuestas de Raúl** · 2 nov 2026

## Fase 4 · Google Drive (carpeta sincronizada G:)

| # | Tema | Decisión |
|---|---|---|
| 1 | Estructura de carpetas | No se crea ni se mueve nada. Raúl indica qué carpeta de G: hace cada papel: material, borradores, generados, aprobados, publicados, informes y encargos. SocialHub solo usa lo mapeado |
| 2 | Nivel del mapeo | En la marca. Cada promoción lo hereda y puede cambiar el papel que necesite |
| 3 | Archivos y publicaciones | Cada pieza del spec.json puede llevar `publicacion` con el id de la publicación. Si no lo lleva, Raúl la asocia desde la app. Nada se adivina |
| 4 | Publicaciones en modo real | Alta y edición desde el centro de control: fecha, hora, plataforma, tipo, título y vía. Los estados siguen su ciclo de vida y nunca pasan a «publicada» sin confirmación |
| 5 | Si G: no está disponible | La tarea queda bloqueada con el motivo, sin gastar intentos, y vuelve a la cola sola cuando la carpeta aparece. Drive se ve en la armería y en los avisos |

## Fase 5 · Orquestador de IA

| # | Tema | Decisión |
|---|---|---|
| 1 | Quién recoge los encargos | Una tarea programada nueva de Claude, «Encargos de SocialHub», en la nube y con el conector de Drive. Las tareas programadas actuales no se tocan hasta validar SocialHub |
| 2 | Frecuencia | 3 veces al día, hacia las 9:40, 14:40 y 20:40 (hora de España) |
| 3 | Primeros encargos | spec.json de la semana (encadenado con la generación de piezas), textos y hashtags, balance semanal e investigación |
| 4 | Modelo | Por tipo de encargo: investigación, textos y spec con Sonnet; balance con Opus. Si falla dos veces, sube un nivel. Se puede cambiar por tipo y por tarea |
| 5 | Límite | 10 encargos a Claude al día por defecto, configurable. Si se llega al límite, el resto espera en la cola con el motivo a la vista. Tokens y coste figuran como «desconocido», porque Claude Pro no los expone |

## Cómo encaja

- SocialHub **no llama a la API de Claude**. Escribe el encargo (`contracts/task-request.v1.schema.json`) en la carpeta de intercambio de Drive.
  - La tarea queda «en espera · encargada a Claude (modelo)».
  - Cuando aparece el resultado (`task-result.v1`), SocialHub lo valida y completa la tarea, la marca con error o la pasa a Raúl (`needs_human`).
- **Proveedor, modelo y agente** van separados: Claude Pro (proveedor), Haiku/Sonnet/Opus (modelos) y «tarea programada en la nube» (agente o runtime). OpenCode podrá añadirse más adelante como otro adaptador, sin tocar campañas ni publicaciones.
- **Registro de cada encargo:** proveedor, modelo, agente, tipo, inicio, fin, duración, estado, error e intentos. Tokens y coste figuran como desconocidos.

## Bloques (una rama y una PR por bloque)

1. **Fase 4 · carpetas y publicaciones:**
   - Papeles de carpeta en marca y promoción.
   - Alta y edición de publicaciones.
   - Asociación de archivos a publicaciones.
   - Comprobación de Drive y tareas bloqueadas por falta de carpeta.
2. **Fase 5 · intercambio con Claude:**
   - Encargos y resultados con validación de los contratos.
   - Tareas en espera de Claude y límite diario.
   - Modelo por tipo, con subida tras dos fallos.
   - Registro de ejecuciones.
3. **Fase 5 · encargos concretos:**
   - spec semanal (al llegar, crea solo la tarea de piezas).
   - Textos y hashtags.
   - Balance semanal.
   - Investigación.
   - La tarea programada «Encargos de SocialHub».

## Bloque 1 · hecho (rama `fase-4/carpetas`)

- **Papeles de carpeta:**
  - `GET/PUT /api/brands/{id}/folders` y `GET/PUT /api/promotions/{id}/folders`. En la promoción solo se guardan las excepciones; la lectura devuelve los papeles en vigor, con su origen y si la carpeta está accesible.
  - Se editan en «Marcas» (marca y promoción) y en la pestaña «Recursos» del centro de control.
  - Las rutas deben ser completas. No se exige que existan al guardarlas, porque G: puede no estar montada en ese momento.
- **Permisos de las piezas:** dan permiso las carpetas con papel y las de «Recursos» de tipo Carpeta (respuesta de Raúl: «Papeles y Recursos»).
- **Carpeta vigilada:** la del papel «Encargos». Si la promoción no lo tiene, sigue siendo `<primera carpeta>/SocialHub/encargos`.
- **Publicaciones:**
  - Alta (`POST /api/promotions/{id}/publications`) y edición (`PUT /api/publications/{id}`). Cambio de estado con `POST /api/publications/{id}/status-changes`.
  - Una publicación nueva empieza como idea, propuesta, en producción o borrador. El identificador es la fecha y la plataforma: `2026-11-10-instagram`.
  - Programar exige al menos 6 horas de margen, y también al cambiar la hora de una publicación ya programada.
  - «Publicando», «Publicada» y «Error de publicación» nunca se ponen a mano. No se edita una publicación que se está publicando o ya publicada.
- **Archivos de cada publicación:**
  - Campo `publicacion` en cada pieza del spec.json. Si la publicación no existe en la promoción, el spec se rechaza antes de generar nada.
  - Asociación a mano: `GET/POST/DELETE /api/publications/{id}/files`. Solo archivos que existen dentro de las carpetas de la promoción, comprobando la ruta real. Quitar la asociación no borra el archivo.
- **G: no disponible:**
  - Si la carpeta de la promoción que contiene el spec no está, la tarea queda bloqueada con el motivo y la carpeta que espera (`waiting_for`), sin gastar intento.
  - Cada 30 s vuelven a la cola las tareas cuya carpeta ya está. «Comprobar de nuevo» la pone en cola al momento.
  - El servicio «Google Drive (carpetas)» de la armería se comprueba cada 30 s. Si falta alguna carpeta, sale en «Necesita atención» (aviso de tipo `ARMORY`) y lleva a la armería.
- **Pruebas:** 60 de backend (incluidas las de extremo a extremo de carpetas, publicaciones, archivos y carpeta desmontada) y 16 de frontal.

