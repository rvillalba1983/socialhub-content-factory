# SocialHub — Fase 3: motor de tareas y herramientas locales

Estado: **plan aprobado por respuestas de Raúl** · 2 nov 2026

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
