# SocialHub — Fase 3: motor de tareas y herramientas locales

Estado: **terminada** (2 nov 2026). Bloques 1, 2 y 3 fusionados en `main`. Falta probarla en el PC de Raúl con su carpeta real de G:.

## Decisiones (respuestas de Raúl, una a una)

| # | Tema | Decisión |
|---|---|---|
| 1 | promo.py | Se pasa a Java. Mismas piezas (carrusel, historia, reel, clip y comprimir) y mismo spec JSON. Las imágenes se componen con Java 2D, sin instalar nada |
| 2 | Tipografía | DejaVu Serif (negrita y cursiva), incluida en la app con su licencia libre. Las piezas salen igual que con promo.py |
| 3 | FFmpeg | Librería `org.bytedeco:ffmpeg` 8.1.2, variante GPL (con H.264), con el ejecutable de cada sistema. No hay que instalar nada |
| 4 | Cola | Sobre la tabla `task`: `SELECT … FOR UPDATE SKIP LOCKED`, hilos virtuales, límite de tiempo y recuperación al arrancar. Sin librería nueva |
| 5 | Carpetas | Lee y escribe directamente en G:, solo en las carpetas permitidas. Nunca sobrescribe: si el archivo existe, usa otro nombre |
| 6 | Encargos | Botón en la estación Generación de contenido para elegir un spec.json, y además una carpeta vigilada de G: |
| 7 | Demo | Modo real y modo demo separados, cada uno con su base de datos. Nunca se mezclan |
| 8 | Modo real | Arranca solo con las 8 estaciones. Raúl da de alta las marcas y las promociones desde la app |
| 9 | Elegir modo | Selector en la cabecera de la app |

## Bloques (una rama y una PR por bloque)

1. **Modos y altas**
   - Bases de datos `socialhub` (real) y `socialhub_demo` en el mismo PostgreSQL incluido. Flyway migra las dos al arrancar.
   - El modo elegido se recuerda en la carpeta de datos.
   - Selector de modo en la cabecera, siempre visible.
   - El simulador solo actúa en modo demo y el motor solo en modo real.
   - Las estaciones se crean con la migración.
   - Formularios y API para dar de alta y editar marcas y promociones.
2. **Motor de tareas**
   - Cola persistente con dependencias, reintentos limitados, límites de tiempo y cancelación.
   - Recuperación de las tareas que se quedaron a medias.
   - Registro de ejecución en el historial de la tarea y en «Operaciones externas».
   - Registro de herramientas locales: qué hay, versión y comprobaciones. En modo real se ve en la armería.
3. **Piezas en Java**
   - Contrato `contracts/promo-spec.v1.schema.json` compatible con el spec actual de promo.py.
   - Carrusel e historia con Java 2D. Reel, clip y comprimir con FFmpeg. Comprobación de 9,5 MB y del LEEME.
   - Carpetas permitidas de G:, sin sobrescribir nunca.
   - Encargo por botón y por carpeta vigilada.

## Seguridad en la ejecución

- Ningún agente envía comandos. Las herramientas se usan mediante operaciones con parámetros validados.
- Rutas solo dentro de las carpetas permitidas, sin `..` ni enlaces que salgan de ellas.
- Límite de tamaño de entrada y de tiempo por operación. Al cancelar se para el proceso.
- Cada llamada a FFmpeg pasa por `ExternalCalls`.

## Cómo funciona el motor (bloque 2)

- **Cola:** cada segundo, el motor bloquea con `SELECT … FOR UPDATE SKIP LOCKED` hasta 2 tareas en cola (`socialhub.engine.max-parallel`).
  - Solo toma tareas de tipos con ejecutor y en estaciones que no estén pausadas ni bloqueadas.
  - Las pasa a «en progreso» y las ejecuta en hilos virtuales.
- **Ejecutores:** cada módulo que aporta trabajo registra un `TaskExecutor` (tipo, agente local y tiempo máximo).
  - El primero es `CHECK_LOCAL_TOOLS`.
  - El bloque 3 añadirá las piezas de promo.py.
- **Errores y reintentos:** si el ejecutor falla, la tarea queda con error y su mensaje. Raúl puede reintentar mientras queden intentos (2) o pasarla a manual.
- **Límite de tiempo:** al agotarse se interrumpe la ejecución y se para el proceso externo. La tarea queda con error «Tiempo agotado».
- **Pausa y cancelación:** interrumpen la ejecución en curso. Al reanudar, la tarea vuelve a la cola.
- **Recuperación:** al arrancar, lo que quedó «en progreso» por un cierre vuelve a la cola sin gastar un intento.
- **Registro de ejecución:** cada paso queda en el historial de la tarea. Cada programa externo (FFmpeg…) queda en «Operaciones externas».
- **Modo fijo:**
  - El motor trabaja siempre sobre la base de datos real y el simulador sobre la demo, aunque la interfaz muestre el otro modo.
  - Los avisos en directo solo llegan si son del modo que se está viendo.
- **Ejecución segura (`SafeProcess`):**
  - Sin intérprete de órdenes y solo programas que SocialHub conoce.
  - Con límite de tiempo, y el proceso se para al interrumpir.
  - Se guarda el final de la salida.
- **Herramientas locales:** FFmpeg 8.1.2 incluido (bytedeco GPL, con H.264) y Java 2D para las imágenes.
  - Se comprueban al arrancar con la tarea «Comprobar herramientas locales» (Generación de contenido).
  - El resultado queda en la armería del modo real.
  - Se puede volver a ejecutar desde la tarea.

## Piezas de promo.py en Java (bloque 3)

- **Tarea `GENERATE_PIECES`:** genera las piezas de un spec.json con el mismo formato que promo.py (`contracts/promo-spec.v1.schema.json`).
  - **Carrusel** (1080×1350) e **historia** (1080×1920): Java 2D con DejaVu Serif, incluida en la app.
  - **Reel**, **clip** y **comprimir**: FFmpeg incluido. Todo vídeo sale en 1080×1920 y por debajo de 9,5 MB.
  - **LEEME** con día, tipo, archivos, texto y hashtags.
- **Comparado con promo.py:** con las mismas imágenes y el mismo spec, las piezas de Java coinciden con las de Pillow. La diferencia media es de menos de 1 nivel de color sobre 255.
- **Carpetas permitidas:**
  - Solo las de la promoción (recursos de tipo «Carpeta»), comprobando la ruta real, así que ni `..` ni enlaces llevan fuera.
  - Solo formatos conocidos y con tamaño máximo.
  - Las rutas del spec pueden ser relativas a la carpeta del spec.
  - Nunca se sobrescribe: si existe, se usa `nombre_2`, `nombre_3`…
- **Encargos:**
  - **Botón** en el panel de la estación Generación de contenido (modo real): lista los spec.json de la carpeta vigilada y permite indicar cualquier otro de las carpetas de la promoción.
  - **Carpeta vigilada** `SocialHub/encargos` dentro de la primera carpeta de la promoción. Cada 30 s, los spec.json nuevos o modificados se convierten en tareas. Si un spec no es válido, la tarea falla con el motivo a la vista.
- **API:** `GET /api/promotions/{id}/piece-specs` y `POST /api/promotions/{id}/piece-orders`.

