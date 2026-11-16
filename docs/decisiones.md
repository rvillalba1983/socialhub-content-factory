# SocialHub — Registro de decisiones

## Reglas de trabajo del proyecto
- **Ante cualquier duda, preguntar antes de actuar** (2026-10-25). Si hay cualquier duda, funcional o técnica, no se hace nada: siempre se pide primero la opinión de Raúl. Las preguntas se plantean de una en una.

## 2026-10-25 · Fase 1: prototipo visual
- Se empieza por un prototipo navegable de la fábrica para validar el zoom semántico, los estados y el centro de control de cada promoción antes de las integraciones.
- El prototipo tiene tres niveles de zoom (fábrica, estación, tarea) y un centro de control por promoción. Usa datos de ejemplo basados en las campañas reales de El DeLito y Litoques, marcados como tales. Las acciones son simuladas.
- La base de reglas de SocialHub son las promociones de Litoques y El DeLito (skill «promociones»):
  - Solo herramientas gratuitas y modo supervisado: Claude prepara y programa, Raúl revisa y pulsa enviar.
  - Nunca enviar correos ni mensajes directos.
  - Nunca publicar al instante: mínimo 6 h de margen.
  - Versos siempre literales.
  - Primero herramientas locales (promo.py).
  - Elección de modelo Haiku, Sonnet u Opus según el tipo de trabajo.

## 2026-10-25 · Plataformas iniciales
- **Instagram:** producción, con las cuentas existentes @elde_lito y @litoques.
- **TikTok:** pruebas. No hay cuentas para ninguna de las dos marcas; Raúl las creará (SocialHub no crea cuentas ni inicia sesión) y se usarán para probar el sistema de extremo a extremo.
- Las primeras publicaciones en TikTok serán privadas, visibles solo para la propia cuenta.
- Hay que verificar los requisitos de acceso de la API oficial de TikTok en la fase de publicación.
- YouTube queda fuera de alcance por ahora.

## 2026-10-25 · Propuesta visual «Warcraft» (guardada)
- Raúl aprueba la dirección visual y pide guardarla tal cual: archivo `propuestas/propuesta-warcraft.html` en este proyecto, y una copia publicada aparte del prototipo de trabajo.
- Rasgos de la propuesta:
  - Mapa de estrategia en tiempo real en pixel art, dibujado por código en canvas. Es original y no usa recursos de Blizzard.
  - Circuito cerrado: estaciones 1–4 arriba, cinta que baja por la derecha, estaciones 5–8 abajo, y camino de vuelta de resultados por la izquierda.
  - Cada estación es un edificio con volumen (fachada, lateral y tejado):
    - Investigación: torre de vigía.
    - Estrategia: sala de mapas.
    - Generación: forja.
    - Revisión: puesto de control.
    - Programación: torre del reloj.
    - Publicación: palomar.
    - Análisis: observatorio.
    - Retroalimentación: molino.
  - Cintas transportadoras con cajas del color de cada marca.
  - Promociones a la izquierda, armería de agentes y herramientas a la derecha. Todo cabe en una pantalla en la vista Fábrica.
  - El estado se ve en el edificio: humo y ventanas encendidas si trabaja, llamas si hay errores, barricada si está bloqueado, banderas del color del estado.
  - Los problemas llaman la atención con un escudo «!» que bota, un anillo pulsante, un panel que parpadea, cajas atascadas y una barra de avisos que lleva al problema.
  - Estética de interfaz: paneles de piedra tallada con remaches dorados, tipografías Pixelify Sans y Alegreya Sans.

## 2026-10-25 · Propuesta visual elegida: «Moderna»
- Raúl elige la propuesta moderna (`propuestas/propuesta-moderna.html`) como base del frontend. La propuesta Warcraft queda guardada como alternativa.
- Rasgos de la propuesta:
  - Anillo de producción con las 8 estaciones como nodos y tarjetas fuera del anillo.
  - Dentro del anillo, las promociones y la tarjeta «Necesita atención».
  - Herramientas y agentes a la derecha.
  - Interfaz monocroma: el color solo comunica estado.
  - Los problemas se señalan con un halo pulsante, un contador en el nodo, el borde rojo de la tarjeta y el tramo de entrada «atascado».
  - Tipografías Geist y Geist Mono; modo claro y oscuro.
- Las funciones validadas son las mismas en las dos propuestas:
  - Zoom semántico en 3 niveles.
  - Panel lateral de estación y tarea.
  - Centro de control por promoción.
  - Buscador.
  - Acciones según el estado.
  - Barra de avisos.

## 2026-10-25 · Fase 0: decisiones de base (respuestas de Raúl)
- **Orden de trabajo:** se cierra la Fase 0 (documento de decisiones técnicas) antes de escribir código.
- **Repositorio:** nuevo y privado en GitHub, `rvillalba1983/socialhub-content-factory`. Lo crea Claude desde el navegador, con la sesión de Raúl.
- **Java:** Java 25, la LTS actual. Hay que confirmar su compatibilidad con Spring Boot 4 en el documento de la Fase 0.
- **Frontend:** React, TypeScript y Vite, a partir de la propuesta moderna. El anillo se dibuja a mano en SVG, sin React Flow.
- **Ejecución:** en el navegador. El backend sirve la web en localhost y un acceso directo la abre. No es una app de escritorio.
- **PostgreSQL:** incluido y arrancado por SocialHub, con binarios propios y sin instalación aparte. Hay que validarlo en Windows y macOS.
- **Sistema prioritario:** Windows primero. macOS se prueba en la fase de distribución.
- **Idioma:** código en inglés; documentación e interfaz en español.
- **IA:** Claude sigue trabajando desde Claude, con tareas programadas y Claude Pro. SocialHub no llama a la API de Claude, que es de pago y no está incluida en Pro.
- **Intercambio con Claude:** carpeta de Google Drive. SocialHub deja encargos en JSON y Claude deja ahí sus resultados, según contratos versionados.
- **Publicación en Instagram:** Publer es la vía principal y la API oficial, la de respaldo. Si fallan las dos, publicación manual con LEEME.
- El detalle está en `claude/fase-0.md`, pendiente de la aprobación de Raúl.
- **Métricas:** opcionales; se obtienen por API oficial o mediante tareas de Claude. No se usa Metricool en SocialHub.

## 2026-10-26 · Prueba de PostgreSQL incluido en Windows: OK
- La prueba se hizo en el PC de Raúl: Windows 10 (build 19045) y PowerShell 5.1.
- Se usaron los binarios portables de PostgreSQL 18.6 de EDB, que necesitan los componentes de Visual C++ (ya estaban instalados).
- Resultado, todo correcto:
  - Creó la carpeta de datos (initdb) en 17 s.
  - Arrancó en 6 s la primera vez y en menos de 1 s las siguientes.
  - Escuchó solo en 127.0.0.1 y no saltó el cortafuegos.
  - Creó la base de datos, escribió datos, paró, volvió a arrancar y los datos seguían ahí.
- Aprendido para el empaquetado:
  - Hay que descomprimir con `tar.exe`, no con `Expand-Archive`, que es muy lento.
  - pgAdmin y StackBuilder se pueden omitir.
  - Hay que comprobar antes que están los componentes de Visual C++.
- Queda validado el riesgo de «PostgreSQL incluido en Windows» de la Fase 0. macOS se valida en la fase de distribución.

## 2026-10-26 · Fase 2: decisiones de diseño
- Las once decisiones de Raúl, tomadas una a una, están en `docs/fase-2.md`. Las principales:
  - Un proyecto Maven con un paquete por dominio.
  - Alcance: marcas, promociones, estaciones, tareas, publicaciones, auditoría, herramientas y agentes.
  - Solo datos de ejemplo, marcados como tales.
  - React desde ya.
  - Gestor propio de PostgreSQL con `pg_ctl`.
  - Carpeta de datos estándar del usuario.
  - Actualización del anillo en directo (SSE).
  - Contraseña local, creada en el primer arranque y recuperable con un archivo `reset-password`.
  - Dirección `localhost:8686`.
- Compilación: Raúl prefiere compilar en el entorno de Claude. Ese entorno tiene cortado el acceso a internet salvo GitHub, lo que incluye Maven Central, npm y la descarga de Java 25. Raúl lo desbloqueará desde el ordenador (acceso de red «Custom» con la lista por defecto más `api.adoptium.net`). Hasta entonces no se escribe código.

## 2026-10-26 · Dudas resueltas en la revisión general
- **Google Drive:** SocialHub accede por la carpeta sincronizada de Google Drive para escritorio (`G:\Mi unidad\…`), como archivos normales. Esto incluye la carpeta de intercambio con Claude. Requiere el PC encendido y Drive sincronizando. La API oficial de Drive queda descartada por ahora.
- **Transición:** las tareas programadas de Claude que llevan hoy las promociones no se tocan hasta que SocialHub funcione de punta a punta. Después se pasan una a una, con el OK de Raúl en cada paso.
- **Fechas de los datos de ejemplo:** relativas al día actual al cargarse, para que el anillo y el calendario siempre muestren algo vivo.
- **Movimiento en la Fase 2:** un simulador del backend hace avanzar las tareas de ejemplo en la base de datos. Solo existe en modo demo y lo indica claramente.
- **Forma de trabajo con Git:** una rama y una pull request por bloque de trabajo. GitHub Actions compila y prueba cada pull request, y se fusiona a `main` solo con el OK de Raúl.

## 2026-11-01 · Revisión de la PR #1: decisiones técnicas (respuestas de Raúl)
- **Seguridad simple:** se quita la contraseña local. La app solo escucha en 127.0.0.1. Se mantiene la cabecera `X-SocialHub-Request` en las peticiones que cambian algo, para que otras webs abiertas en el navegador no puedan actuar.
- **Persistencia:** Spring Data JPA. Los filtros dinámicos se hacen con Specifications de Spring Data, que funcionan sobre Criteria.
- **Auditoría:** Hibernate Envers. Guarda el historial de versiones de las entidades auditadas, con quién y cuándo.
- **API first:** el contrato OpenAPI es la fuente de la verdad. openapi-generator (generador `spring`, solo interfaces) genera los modelos y las interfaces de los controladores.
- **Diseño REST según Zalando:**
  - Propiedades JSON en snake_case.
  - Las acciones sobre tareas son comandos: `POST /api/tasks/{task_id}/commands` con `{"type": "PAUSE"}`. Los valores fijos (estados, tipos) van en MAYÚSCULAS.
- **Mapeo entre API, dominio y JPA:** MapStruct.
- **Código:** records siempre que se pueda. Lombok está permitido para no escribir constructores.
- **Arquitectura hexagonal + DDD:** se marca con jMolecules (@AggregateRoot, @ValueObject, puertos y adaptadores) junto con Spring Modulith, con pruebas que verifican los límites.
- **Observabilidad de operaciones externas:**
  - Cada operación externa (Drive, Publer, APIs, herramientas locales, PostgreSQL…) pasa por la Observation API de Micrometer, con duración, resultado, error y un ID de traza en los logs.
  - Además se guarda en una tabla y se muestra en SocialHub, en una pantalla «Operaciones externas».
  - No hace falta ningún servidor extra.
- **Base de datos:** revisar la optimización desde ya (consultas N+1, índices, paginación).

## 2026-11-02 · Revisión de la PR #1: cómo se ha aplicado
Detalles de implementación que no cambian ninguna respuesta de Raúl; se pueden revisar en la PR.
- **Hexagonal:** cada módulo (`production`, `publishing`, `promotion`, `armory`) tiene:
  - `domain`: agregados y puertos, sin Spring ni JPA.
  - Raíz del módulo: casos de uso y eventos de dominio.
  - `adapter/in/web`: controladores que implementan las interfaces generadas.
  - `adapter/out/persistence`: entidades JPA, repositorios Spring Data y mapeadores MapStruct.
  - El dominio no es la entidad JPA. Cuesta un mapeador más por módulo, pero el dominio no depende de la base de datos.
- **Eventos entre módulos:** se atienden en la misma transacción, como antes. Por ejemplo, al aprobar la revisión se aprueban las publicaciones en revisión de esa promoción.
- **Envers:**
  - Solo audita los campos que importan: estado, pausa, agente, motivo, error, resultado e intentos de las tareas; estado, pausa y nota de las estaciones; estado, incidencia y confirmación de las publicaciones.
  - El avance y los pasos no se auditan, porque cambian a cada momento.
  - Como solo hay un usuario, se guarda cuándo cambió algo, no quién.
- **Operaciones externas:**
  - `ExternalCalls` (en `platform`) envuelve cada llamada con la Observation API de Micrometer.
  - El módulo `operations` las acumula en memoria y las guarda por lotes cada 2 s. Así queda registrado incluso el arranque de PostgreSQL, que ocurre antes de que haya base de datos.
  - Se borran a los 30 días.
  - Hoy se observan la extracción de binarios, `initdb`, el arranque y la parada de PostgreSQL. Drive, Publer y las APIs pasarán por el mismo punto cuando existan.
- **Base de datos:**
  - Los enumerados se guardan con su nombre, en MAYÚSCULAS.
  - Las listas de textos simples se guardan en columnas `text[]`.
  - Las colecciones se cargan por lotes (`default_batch_fetch_size: 50`). La vista completa del anillo hace 16 consultas, y una prueba vigila que no pase de 20.
  - Hay índices en todas las claves foráneas, más índices parciales para las tareas activas y para las operaciones fallidas.
  - Bloqueo optimista con `@Version`.
  - Hibernate valida el esquema contra Flyway al arrancar.
- **Corrección:** la hora de las publicaciones se desplazaba una hora por la zona horaria de JDBC. Ahora se guarda tal cual, en hora de España (columna `timezone`).
- **Promociones y armería:** de momento solo lectura. Los valores fijos (estado de una automatización, tipo de recurso…) los valida la base de datos.

## 2026-11-02 · Frontal adaptado a la nueva API
- **Tipos generados (respuesta de Raúl):** openapi-typescript genera los tipos TypeScript desde `contracts/openapi.yaml`.
  - openapi-typescript 7.13 todavía no funciona con TypeScript 7.
  - Por eso va aislado en `frontend/tools/openapi`, con su propio TypeScript 5.9 y solo para generar.
  - CI comprueba que los tipos coinciden con el contrato.
- **Sin pantalla de acceso,** como el backend.
- **Pantalla «Operaciones externas»** en la cabecera, con filtros por resultado y sistema, paginación y detalle del error y de la traza.
- **Tarjeta «Necesita atención»:** muestra los 2 avisos más importantes (primero los errores) y resume el resto, para no tapar las estaciones de abajo. Lo mismo hace la barra de avisos con 3.
- **Rama:** el trabajo del bloque 2 se ha rebasado sobre `main` y sigue en `fase-2/frontend`. La rama anterior, `fase-2/frontend-react`, queda sin usar, porque reescribirla necesitaba un push forzado.

## 2026-11-02 · Fase 3: decisiones (respuestas de Raúl)
- **promo.py:** se pasa a Java (Java 2D para las imágenes), con el mismo spec JSON y las mismas piezas.
- **Tipografía:** DejaVu Serif, incluida en la app.
- **FFmpeg:** librería `org.bytedeco:ffmpeg` 8.1.2, variante GPL, con el ejecutable de cada sistema.
- **Cola:** sobre la tabla `task` (`SKIP LOCKED`), sin librería nueva.
- **Archivos:** lee y escribe en G:, solo en las carpetas permitidas y sin sobrescribir nunca.
- **Encargos de piezas:** botón en Generación de contenido y carpeta vigilada.
- **Modos:** real y demo separados, cada uno con su base de datos. Se eligen con un selector en la cabecera.
- **Modo real:** arranca con las 8 estaciones. Las marcas y las promociones se dan de alta desde la app.

Detalle en `docs/fase-3.md`.

## 2026-11-02 · Fase 3, bloque 2: detalles del motor
- **Reintentos:** son manuales y limitados, con 2 intentos, como hasta ahora. El motor no reintenta solo, para no repetir un error sin que Raúl lo vea.
- **Reanudar:** una tarea reanudada vuelve a la cola, en lugar de pasar directamente a «en progreso». Así el motor la retoma de verdad.
- **Modo fijado por hilo:** el motor (real) y el simulador (demo) fijan su base de datos aunque la interfaz cambie de modo. Así nunca se escribe en la base de datos equivocada.
- **Envers con dos bases de datos:** cada número de revisión se pide a la secuencia de su base de datos (migración `V3`), sin bloques en memoria compartidos.
- **Entidades nuevas:** la versión es nula hasta guardarlas, para que se inserten en lugar de fusionarse.

## 2026-11-02 · Fase 3, bloque 3: piezas
- **Carpetas de una promoción:** son sus recursos de tipo «Carpeta». Solo ahí se lee y se escribe.
- **Carpeta vigilada:** `SocialHub/encargos` dentro de la primera carpeta de la promoción.
- **Volver a ejecutar** una tarea de piezas no pisa lo anterior: genera archivos numerados.
- **Encargar desde el botón** se permite siempre, incluso para un spec ya encargado. La carpeta vigilada solo encarga lo nuevo o modificado.

## 2026-11-02 · Fases 4 y 5: decisiones (respuestas de Raúl)
- **Carpetas:** Raúl indica qué carpeta de G: hace cada papel. Se mapea en la marca y la promoción puede cambiar el papel que necesite. No se crea ni se mueve nada.
- **Archivos y publicaciones:** campo `publicacion` en cada pieza del spec. Si no lo lleva, la asociación se hace a mano en la app.
- **Publicaciones en modo real:** alta y edición desde el centro de control.
- **Sin G::** la tarea se bloquea sin gastar intentos y vuelve a la cola sola cuando la carpeta aparece.
- **Encargos a Claude:**
  - Los recoge una tarea programada nueva, «Encargos de SocialHub», 3 veces al día.
  - Los primeros tipos son spec semanal, textos y hashtags, balance semanal e investigación.
  - El modelo va por tipo de encargo y sube un nivel tras dos fallos.
  - Como mucho 10 encargos al día, configurable.

Detalle en `docs/fase-4-5.md`.

## 2026-11-02 · Fase 4, bloque 1: carpetas, publicaciones y G: no disponible
- **Permisos de las piezas:** los dan las carpetas con papel y las de «Recursos» de tipo Carpeta (Raúl: «Papeles y Recursos»).
- **Carpeta vigilada:** la del papel «Encargos». Sin ese papel, se mantiene `SocialHub/encargos` dentro de la primera carpeta.
- **Publicaciones nuevas:**
  - Empiezan antes de la revisión: idea, propuesta, en producción o borrador.
  - Su identificador es `<fecha>-<plataforma>`, numerado si se repite.
- **Cambios de estado a mano:**
  - Siguen el ciclo de vida.
  - Programar exige al menos 6 horas de margen.
  - Los estados de publicación (publicando, publicada, error) solo los pone SocialHub.
- **Carpeta que falta:**
  - Bloquea la tarea sin gastar intento.
  - Cada 30 s se comprueba y la tarea vuelve sola a la cola.
  - Drive aparece en la armería y, si falta algo, en los avisos (tipo `ARMORY`).
- **Módulo `drive`:** comprueba las carpetas de Drive. No llama a la API de Google: solo mira las carpetas sincronizadas en el ordenador.

## 2026-11-02 · Fase 5, bloque 2: intercambio con Claude
- **Carpeta de intercambio:** por marca. Es la del papel «Encargos», con `para-claude` y `de-claude`.
- **Validación de resultados:**
  - Con la librería `com.networknt:json-schema-validator` 3.0.8 (gratuita, Apache 2.0, Jackson 3), contra el propio archivo del contrato, que se copia al jar.
  - El `$id` del contrato no es una URI y la librería no lo admite. Se quita al cargarlo, sin cambiar el contrato.
- **Intentos de los encargos a Claude:** 3. Los dos primeros usan el modelo de la tarea y el tercero sube un nivel. Reintentos a mano.
- **Límite diario:** se cuenta por envíos (cada intento cuenta) desde las 0:00, hora de España.
- **Sin carpeta de «Encargos»:** el encargo sigue en cola con el motivo, sin bloquearse, y sale en cuanto se configura.
- **Motor:** tomar tareas de la cola y recuperar las que quedaron a medias ya no se cruzan (aparecía en las pruebas).

## 2026-11-02 · Fase 5, bloque 3: encargos concretos (respuestas de Raúl)
- **spec semanal:**
  - Va dentro del resultado (`outputs.spec`). SocialHub lo valida y lo guarda en `Encargos/specs-de-claude`, una carpeta no vigilada.
  - SocialHub encarga las piezas él mismo.
- **Textos y hashtags:** se guardan en la publicación (campos nuevos) para revisarlos antes de programar.
- **Balance e investigación:** el informe Markdown se guarda en la carpeta de Informes, con las fuentes.
- **Encargos automáticos:** spec el domingo y balance el lunes, desde las 9:00, por promoción activa. El resto, a mano.
- **Validaciones fuera de transacciones anidadas:** los manejadores comprueban todo antes de llamar a servicios transaccionales. Así un error no deja marcada para deshacer la transacción del resultado de Claude.
- **Carpeta de salida del spec:** se comprueba al leer el spec, no solo al generar.
- **Tarea programada «Encargos de SocialHub»:**
  - Creada con el OK de Raúl: 9:40, 14:40 y 20:40, hora de España, en la nube, con aprobación automática y aviso en el móvil.
  - Cada encargo lo hace un subagente con el modelo del encargo.
  - La sesión principal quedó en Opus porque al crearla no se puede elegir su modelo; se puede bajar a Haiku en sus ajustes.

## 2026-11-02 · Fase 6: decisiones (respuestas de Raúl)
- **Orden:** primero identidad y calidad; después memoria editorial y propuestas.
- **Identidad de la marca:** tono, público, temas vetados, expresiones preferidas y prohibidas, estilo visual, llamada a la acción y hashtags fijos. Se fija en la marca y las promociones la heredan.
- **Control de calidad:** bloquea «Aprobada» y «Programada» mientras haya fallos sin aceptar. Cada aceptación queda anotada con su motivo.
- **Revisión con criterio:** la hace Claude a petición, con el encargo «Revisión de calidad» (Haiku por defecto).

Detalle en `docs/fase-6.md`.

## 2026-11-03 · Fase 6, bloque 2: memoria editorial (respuestas de Raúl)
- **Qué se recuerda:** lo publicado, los rechazos con su motivo, las correcciones a textos de Claude y las decisiones editoriales por marca.
- **Cuánto va a Claude:** 8 semanas de lo publicado, 20 rechazos, 20 correcciones y todas las decisiones, solo de la misma marca.
- **Motivo:** fijo más una nota opcional. Es obligatorio al cancelar una publicación o descartar un texto de Claude.

## 2026-11-03 · Fase 6, bloque 3: propuestas (respuestas de Raúl)
- **Propuestas:** encargo semanal «Propuestas de la semana». Sale solo los lunes desde las 18:00, para la semana siguiente; si SocialHub estaba cerrado, el martes o el miércoles.
- **Revisión:** cada propuesta se aprueba (pasa a «En producción») o se rechaza con motivo (se cancela y queda en la memoria editorial).
- **El spec semanal** solo tiene en cuenta las publicaciones aprobadas.

## 2026-11-03 · Fases 7, 8 y 9: decisiones (respuestas de Raúl)
- **Publicación:**
  - Vías por orden: Publer, Publora y API oficial. El calendario cuenta con lo que haya en las tres.
  - Publer se programa con Claude y Chrome en el PC de Raúl, porque su API es de pago.
  - Solo se programa lo que Raúl pasa a «Programada».
  - Claude confirma «Publicada» comprobándolo.
  - Si fallan las tres vías, se reintenta mientras haya margen y luego pasa a manual con LEEME.
- **Métricas:** de Metricool y, si se puede, de Publora o la API oficial. Sin inventar.
- **Autonomía:** SocialHub reintenta encargos y pide textos y revisiones solo. Todo lo demás lo decide Raúl.
- **Avisos:** en la app y en un resumen diario.
- **Distribución:**
  - Instalador para Windows y macOS.
  - Copia diaria en Drive, guardando 14.
  - SocialHub no arranca solo.

Detalle en `docs/fases-7-9.md`.


## 2026-11-03 · Fase 7, bloque 1: motor de publicación
- **Respuestas de Raúl:** los encargos con Chrome van a `Encargos/para-claude-pc` (la tarea de la nube no los ve); la clave de Publora, en un archivo local fuera de la base; los encargos de publicación no cuentan en el límite diario.
- **Encargos nuevos:** «Programar en Publer» (estación Programación) y «Comprobar publicación» (estación Publicación). Los crea SocialHub; no se piden a mano. Sonnet por defecto.
- **Tiempos de Publer:** Claude debe programarla antes de 8 h de la hora (la «deadline» del encargo). SocialHub espera su resultado hasta 7 h antes y después prueba Publora y la API oficial hasta 6 h antes. Si se programa con menos de 8 h, Publer se salta.
- **Reintentos:** si fallan todas las vías, se vuelve a empezar cada hora mientras el siguiente intento quede antes de las 6 h. Si no, LEEME en la carpeta de aprobados (o generados, o la primera que haya), incidencia en la publicación y aviso.
- **Comprobación:** a la hora pasa a «Publicando»; 15 min después (2 h si es a mano) se encarga la comprobación. «Publicada» solo con enlace https o identificador. Si no aparece, o la comprobación no llega en 24 h, «Error de publicación» y aviso.
- **Resultados tardíos:** si Publer la programa cuando SocialHub ya siguió por otra vía, la publicación queda marcada como «Posible duplicado» y avisa.
- **Sacarla de «Programada»** detiene la publicación. Si ya estaba programada en Publer, avisa para borrarla allí a mano (SocialHub no la borra).
- **Incidencias:** Raúl las da por resueltas desde la publicación.
- **Programada no se edita** (respuesta de Raúl): ni datos, ni texto, ni archivos. Para cambiarla, vuelve a «Aprobada» (avisa si hay que borrarla en Publer) y se programa otra vez. Las piezas generadas no se asocian a una publicación ya programada.

Detalle en `docs/fase-7.md`.

## 2026-11-03 · Fase 7, bloque 2: Publora
- **Cuenta de cada marca** (respuesta de Raúl): la elige él en la marca, una por red, de las conectadas en Publora.
- **Desprogramar** (respuesta de Raúl): SocialHub borra en Publora la publicación que creó y lo anota.
- **Clave:** en `secretos/publora.key` de la carpeta de datos, solo legible por el usuario; se comprueba con Publora antes de guardarla.
- **Cliente HTTP:** el de Java; ninguna librería nueva.
- **Comprobación:** consulta a Publora cada 5 minutos desde 15 minutos después de la hora, hasta 24 h.

Detalle en `docs/fase-7.md`.

## 2026-11-03 · Fase 7, bloques 3 y 4
- **Bloque 3 aparcado** (respuesta de Raúl): primero el calendario. La API oficial tiene pegas (URL pública para imágenes, sin programación, TikTok privado hasta revisar la app).
- **Calendario** (respuesta de Raúl): Publora por su API cada hora; Publer y la red con el encargo diario «Leer calendario» con Chrome en el PC, que no cuenta en el límite diario.
- Lo de fuera de SocialHub también se pasa a las propuestas de la semana, para no pisarlo.
- El identificador de la vía (p. ej. el grupo de Publora) se conserva al confirmar «Publicada».

Detalle en `docs/fase-7.md`.

## 2026-11-03 · Fase 8, bloque 1: reintentos automáticos
- Los encargos a Claude que fallan se reintentan solos hasta el tercer intento (con subida de modelo), 30 minutos después del fallo.
- Los de publicación con Chrome y los que piden ayuda a Raúl no se reintentan solos.

Detalle en `docs/fase-8.md`.

## 2026-11-03 · Fase 8: respuestas de Raúl y bloque 2
- **Textos solos** para lo que está en producción sin texto; **revisión sola** al pasar a «Pendiente de revisión», una vez por versión del texto.
- **Métricas:** sin Metricool. API de Publora y Claude con Chrome; cada publicación se mide una vez, a los 7 días.
- **Resumen diario:** solo en la app.

Detalle en `docs/fase-8.md`.

## 2026-11-03 · Fase 8, bloque 3: métricas
- Las lee Claude con Chrome (encargo «Leer métricas», no cuenta en el límite diario), una vez por publicación, a los 7 días. La API de Publora no sirve: sus estadísticas son solo de Mastodon y Bluesky, y de pago.
- Hasta 3 intentos, uno al día. Lo que no se ve, sin dato.
- El balance semanal recibe las métricas.

## 2026-11-03 · Fase 8, bloque 4: resumen de hoy
- Solo en la app (respuesta de Raúl): tarjeta «Resumen de hoy» en el mapa.

## 2026-11-03 · Documentación funcional
- Manual funcional en `MANUAL.md` (petición de Raúl), con capturas del vídeo demo en `docs/manual/img/`.
- Diagramas en D2 con los colores de la web (`docs/diagramas/`, estilo común en `estilo.d2`). Los SVG se generan con `generar.sh` y se suben al repositorio. D2 es solo una herramienta de documentación (gratuita): no forma parte de la app.

## 2026-11-03 · Mapa 3D del código
- Petición de Raúl: vista 3D de todo el código con zoom semántico (isla → capas → archivos → código), generada por un script en cada PR.
- Se ve la arquitectura (respuesta de Raúl: «Hexágonos por capas»): cada módulo del monolito es una isla hexagonal, con el dominio en el centro, la aplicación alrededor y los adaptadores fuera (entrada a la izquierda, salida a la derecha). El frontal React es otra isla y los contratos, el puente. Los puentes entre islas salen de los `import` entre módulos.
- Script Node en `scripts/mapa-codigo/` con Three.js (gratuito, MIT) incrustado: el resultado es un único `index.html` que funciona sin servidor ni internet. Es una herramienta de desarrollo: no forma parte de la app.
- Workflow `mapa-codigo.yml`: solo en Linux, sube el mapa como artefacto (30 días) y deja o actualiza un comentario en la PR con el enlace.
- **Capas como unidades** (petición de Raúl): cada capa es un bloque macizo con una celda por clase, así que su superficie es su número de clases (respuesta de Raúl: clases, no líneas). Al acercarte a una capa, el bloque se abre y salen sus clases. Los niveles pasan a General → Isla → Capa → Código.

## 2026-11-03 · Fase 9: respuestas de Raúl
- **Copias:** al abrir SocialHub, si hoy no hay copia; se guardan 14; carpeta única en los ajustes generales.
- **Formato:** exportación de tablas con el driver de PostgreSQL (el PostgreSQL incluido no trae `pg_dump`).
- **Restaurar:** copia previa y confirmación escribiendo el nombre; después SocialHub se para y pide reabrirlo.
- **Instaladores:** `.msi` y `.dmg` sin firmar.

Detalle en `docs/fase-9.md`.

## 2026-11-08 · Pruebas en otros sistemas
- Respuesta de Raúl: hasta tener una versión utilizable, cada PR se prueba en Linux y Windows (`otros_sistemas=windows`). Después, también en macOS.

## 2026-11-08 · Fase 9, bloque 1: copias
- **Sin carpeta elegida** (respuesta de Raúl): la copia se hace igualmente en la carpeta local de SocialHub y sale un aviso en «Necesita atención». Pasa lo mismo si la carpeta elegida no está disponible.
- Se guardan las 14 copias más recientes de cada carpeta, de cualquier tipo (diaria, a mano o antes de restaurar).
- Solo se copia la base de datos real: la de demo tiene datos de ejemplo.
- El driver de PostgreSQL pasa a usarse al compilar (antes solo en ejecución), para usar su API de `COPY`. No es una librería nueva.
- Si la restauración falla antes del cambio de esquema, los datos actuales no se tocan y SocialHub sigue trabajando.

Detalle en `docs/fase-9.md`.

## 2026-11-08 · Fase 9, bloque 2: instalador de Windows
- **Solo `.msi` por ahora** (respuesta de Raúl). El workflow ya tiene el `.dmg`, que se lanzará al pasar a probar en macOS.
- **Cerrar desde la web** (respuesta de Raúl): botón «Cerrar» en la barra, sin icono en la bandeja. El acceso directo abre el navegador, y si SocialHub ya está abierto, solo abre el navegador.
- Se instala en Archivos de programa (pide permiso de administrador una vez). La instalación solo para el usuario no vale: WiX la rechaza (ICE64) en otra carpeta, y la carpeta por defecto coincide con la de datos.
- WiX 3.14 (gratuito) para crear el `.msi`. Los textos del instalador van sin tildes, porque WiX usa la página de códigos 1252.
- Los datos (`%LOCALAPPDATA%\SocialHub`) no se tocan al instalar, actualizar ni desinstalar.

Detalle en `docs/fase-9.md`.

## 2026-11-08 · Arranque en el PC de Raúl
- **Primera prueba del `.msi`:** falló porque PostgreSQL para Windows necesita las bibliotecas de Visual C++ (`vcruntime140`, `vcruntime140_1` y `msvcp140`), y en el PC de Raúl no están instaladas (error 0xC0000135). En la máquina de GitHub sí estaban, por eso la prueba pasó allí.
  - Solución: SocialHub copia junto a PostgreSQL las mismas bibliotecas que trae Java. Son redistribuibles.
  - El workflow comprueba ahora que están.
- **SocialHub arrancó dos veces a la vez** (dos clics mientras tardaba en arrancar).
  - Ahora el primero bloquea `<datos>/socialhub.lock`. Los siguientes esperan a que esté listo y solo abren el navegador.
  - El workflow lo prueba con dos arranques seguidos.
- **Segunda prueba del `.msi`:** seguía sin arrancar. Las bibliotecas sí se copiaron; el problema de fondo era otro. En el doble arranque del primer día, el segundo arranque borró a medias el PostgreSQL que había descomprimido el primero (entre otros archivos, `zlib1.dll`), y la marca «completo» se quedó.
  - Ahora la marca lista todos los archivos descomprimidos. Al arrancar se comprueba que están; si falta alguno, PostgreSQL se descomprime de nuevo solo.
  - El workflow lo prueba borrando `zlib1.dll`.
- Una carpeta de datos a medias (initdb que no terminó) se aparta como `pgdata-incompleta-…`, sin borrarla, y se vuelve a crear.
- **Respuesta de Raúl:** no se abre una página de espera al arrancar. El navegador se abre cuando SocialHub está listo.

## 2026-11-08 · Puesta en marcha de marcas y promociones
Tras la primera prueba de Raúl con una marca y una promoción nuevas:
- **Carpetas** (respuesta de Raúl: «las dos cosas»):
  - Al dar de alta la marca, una carpeta base opcional: SocialHub crea dentro Material, Borradores, Generados, Aprobados, Publicados, Informes y Encargos, y las asigna.
  - En «Carpetas de Drive», el botón «Crear las que faltan».
  - Cambia la decisión anterior de que SocialHub no crea carpetas: ahora crea las que falten, pero nunca borra ni mueve nada.
- **Avisos** (respuesta de Raúl: solo los avisos, sin lista de puesta en marcha): un encargo a Claude que no puede salir por algo que tiene que arreglar Raúl sale en «Necesita atención», con el motivo. Por ejemplo, la marca sin carpeta de «Encargos».
  - El límite diario no cuenta, porque es lo esperado.
  - La lista se recalcula en cada envío (cada 30 s), sin guardarla.
- **Primera semana** (respuesta de Raúl: pedirlas al crearla): si al dar de alta (o activar) una promoción ya pasó el lunes a las 18:00 en que se habrían pedido sus primeras propuestas, se piden enseguida, solo para los días que quedan de esa semana. Se pide una sola vez, con la misma clave que el encargo semanal.

## 2026-11-09 · Forma de trabajar
- **Una fase por conversación** (petición de Raúl): todo lo necesario para retomar está en el repositorio (`docs/estado.md`, `docs/fase-N.md`, `docs/como-trabajar.md`).
- **Subagentes** (petición de Raúl: usar distintos niveles de modelo y esfuerzo): `.claude/agents/` con `vigilante-ci` y `explorador` (Haiku, bajo), `documentador` y `probador-visual` (Sonnet, bajo), `programador` (Sonnet, medio) y `revisor` (Opus, alto). Las preguntas, el diseño y la revisión con Raúl siguen en la sesión principal.
- **Más trabajo en GitHub para ahorrar tokens** (petición de Raúl):
  - Las PR con cambios en el backend se prueban en Windows automáticamente, sin lanzarlas a mano.
  - **Respuesta de Raúl:** las pruebas del backend, solo en Windows; ya no en Linux. El frontal, el mapa 3D y los diagramas siguen en máquinas Linux de GitHub, porque no prueban la app en un sistema concreto y gastan la mitad de minutos.
  - Las pruebas fallidas se anotan con su error, y `scripts/estado-pr.sh` lo resume en pocas líneas.
  - Los SVG de los diagramas D2 se generan en GitHub (`diagramas.yml`).
  - La batería completa la pasa GitHub; en local, solo las pruebas afectadas.
- El entorno en la nube se prepara con `scripts/entorno/preparar.sh`. Antes, los envoltorios se perdían al reiniciar el contenedor.

## 2026-11-09 · Fase 10: guía y propuestas iniciales
- **Guía** (respuesta de Raúl): bloque «Qué hacer ahora» en la pantalla principal, con recomendaciones que desaparecen al hacerlas. La lista de puesta en marcha sigue descartada.
- **Propuestas iniciales** (respuesta de Raúl): mensaje con su estado y hora aproximada de llegada, y aviso cuando lleguen. Sin ideas de plantilla y sin cambiar las tareas programadas.
- **Publora** (respuesta de Raúl): la recomendación sale hasta conectar la cuenta o pulsar «No uso Publora» en esa marca, y se puede deshacer en su tarjeta de Publora.
- **Datos de las recomendaciones** (técnica): el descarte va en su propia tabla (`brand_publora_dismissed`), dentro del módulo de publicación. `promotion_id` y `eta_text` viajan como campos propios para no deducirlos del texto.
- **Encargos sin recoger** (técnica): la recomendación ignora los encargos «para-claude-pc», porque los recoge otra tarea.
- **Franja encima del mapa** (respuesta de Raúl): «Qué hacer ahora» va a ancho completo encima del mapa, fuera del zoom, con scroll propio (máximo 45 % de la altura) para no tapar el mapa.
- **«Ver N más»** (respuesta de Raúl): se ven las 3 más importantes y un botón «Ver N más» / «Ver menos» despliega el resto. El backend devuelve la lista completa, sin recortar; las propuestas retenidas de una promoción salen solo ahí.
- **Filas 4 y 7 ajustadas tras la revisión** (técnica): la fila 4 solo se mira si la carpeta de «Encargos» de la marca está disponible, y la fila 7 pide activar una promoción planificada o en pausa antes que dar de alta una nueva.
- **Merge de la PR #32** (petición de Raúl): lo hizo Claude porque Raúl lo pidió expresamente para esta PR; la regla general sigue siendo que solo Raúl hace merge.

## PC de Raúl siempre disponible (11 nov 2026)

- **Siempre abiertos** (petición de Raúl): un script de Windows (`scripts/windows/siempre-abierto/`) mantiene abiertos Claude de escritorio, OpenCode Desktop y Chrome, y los reabre si se cierran.
- **Sin suspensión** (respuesta de Raúl): mientras el script está en marcha, Windows no se duerme; la pantalla sí puede apagarse.
- **SocialHub también** (respuesta de Raúl): se abre al iniciar sesión y se reabre solo si se cae; si se cierra con «Cerrar», se respeta hasta el siguiente inicio de sesión. Se distingue por su registro (técnica).
- **Cómo arranca** (técnica): acceso directo en la carpeta «Inicio» del usuario, sin ventana y sin permisos de administrador. Abre los programas por su nombre en el menú Inicio, sin rutas fijas. Se prueba en Windows con `.github/workflows/siempre-abierto.yml`.

## Fase 11 · Arreglos de la primera semana real (11 nov 2026)

- **Semana fija** (técnica): un encargo de propuestas toma la semana que lleva escrita en sus datos (se escribe al crearlo). Los antiguos, sin fecha, toman la semana del último archivo de «para-claude» que se les envió (`inputs.week.from`) y, si no se puede leer, el lunes siguiente al día en que se crearon; nunca la de cuando se lee el resultado.
- **Planificadas** (respuesta de Raúl): pasan solas a «Activa» el día que empiezan, y sus propuestas de la primera semana se piden igual que las de una activa.
- **Aviso de la activación** (respuesta de Raúl): el día que una promoción pasa sola a «Activa», «Qué hacer ahora» enseña una fila informativa, sin botón, al final: «Promoción «X» activada sola: empieza hoy». Desaparece al día siguiente (hora de España) y no sale si Raúl la activó a mano. Técnica: se anota en `ai_auto_order` con el tipo `PROMOTION_AUTO_STARTED` y el día, sin migración.
- **Propuestas no válidas** (respuesta de Raúl): se quitan solo las malas y entran las buenas. Son no válidas las que tienen fecha u hora ilegible, una red desconocida, caen fuera de la semana o de las fechas de la promoción, o son para dentro de menos de 6 horas (margen aprobado por Raúl). Si no queda ninguna, el encargo falla y se reintenta.
- **Propuestas paradas** (respuesta de Raúl): la fila de retenidas de la franja dice también si es por Drive no disponible o por el límite diario. Para el límite, lleva a la pantalla «Claude».
- **Al momento** (respuesta de Raúl): las propuestas de la primera semana se piden al guardar la promoción, sin esperar a la ronda de 10 minutos.
- **Especificación** (respuesta de Raúl): no se pide mientras la semana no tenga publicaciones aprobadas, y la franja lo dice.
- **Avisar si falta algo para publicar** (petición de Raúl): «Agentes y herramientas» enseña Claude en la nube, Claude en el PC · Publer y Publora. «Necesita atención» avisa si la tarea del PC no contesta o si no hay forma de publicar.
- **Tarea «Publicar con Chrome»** (OK de Raúl): reactivada el 11 nov. Estaba desactivada desde el 8 nov, tras fallar por el límite de uso.
- **Buffer** (respuesta de Raúl, 11 nov): descartado para siempre. No se integrará en SocialHub.
- **Fase 12 única** (respuesta de Raúl, 11 nov): todo lo pendiente va en una sola fase, por bloques. Contenido inicial:
  - pestaña «Métricas» de la promoción, con las dudas en `docs/estado.md`;
  - instalador de macOS;
  - API oficial de Instagram, junto con un estudio de si programar desde la web de Instagram o desde Meta Business Suite con Chrome puede sustituir a Publer.
  - TikTok en SocialHub (cuenta personal de Raúl, `@litoques.eldelito`, para las dos marcas) y detalles visuales de «Necesita atención».
- **Ventana del encargo semanal** (decisión de Raúl, 11 nov): las propuestas de W+1 se piden desde el lunes de W a las 18:00 hasta el domingo de W a las 23:59; al guardar una promoción sin margen en su primera semana, o de jueves a domingo, se piden también en el acto, sin duplicar.
- **Merge de las PR #33, #34 y #35** (petición de Raúl, 11 nov): los hizo Claude porque Raúl lo pidió expresamente; la regla general sigue siendo que solo Raúl hace merge.
- **Reloj fijo en las pruebas de integración que crean promociones** (técnica, 12 nov): `PinnedClock` (martes 10:00, Madrid, avanza en tiempo real). Sin él, desde el jueves el guardado pide también las propuestas de la semana siguiente y fallaban 6 pruebas según el día. No es un fallo del código: es el comportamiento decidido el 11 nov.

## Fase 12 · Bloque 2: TikTok y cuenta de Publer por marca (12 nov 2026)

- **TikTok con cuenta personal** (decisión de Raúl): la de empresa exige demostrar que es una empresa real. Se usa la cuenta personal «Lito · Litoques · El DeLito» (`@litoques.eldelito`) para las dos marcas.
- **Públicas desde la primera** (decisión de Raúl): las publicaciones de TikTok salen con privacidad «Todo el mundo», sin pasar por «Solo yo».
- **Sin separación entre marcas** (decisión de Raúl): las dos marcas publican en la misma cuenta de TikTok, sin separarlas.
- **Cuenta de Publer por marca y red** (decisión de Raúl): cada marca indica en su ficha (tarjeta «Cuenta en Publer») el nombre de su cuenta de Publer en Instagram y en TikTok. Dos marcas pueden indicar la misma. Técnica: tabla `brand_publer_account(brand_id, platform, account_name)`, clave (marca, red), sin unicidad sobre el nombre (migración V24); la API es `/api/brands/{id}/publer-accounts`.
- **Los encargos de Publer dicen la cuenta** (decisión de Raúl): «Programar en Publer», «Comprobar publicación» y «Leer calendario» llevan la cuenta (`publer_account`, o `publer_accounts` en el calendario) y `how` dice «en la cuenta «X» de Publer».
- **Comentarios, Dúo y Stitch activados** (decisión de Raúl): en TikTok, además, los sonidos ya van en el vídeo, así que no se añade música. Va en `how` de los encargos de TikTok.
- **Sin cuenta, retenida** (decisión de Raúl): si la marca no tiene cuenta de Publer en esa red, no se crea el encargo. La publicación queda «Retenida» con el motivo visible y «Qué hacer ahora» pide «Indica la cuenta de Publer de {marca} para {red}», con botón a la tarjeta de la ficha. Técnica: estado nuevo `HELD` de la tarea de publicación; se vuelve a probar cada 5 minutos; si Publer deja de llegar a tiempo (8 h antes), se sigue por Publora como siempre.
- **Límite de 10 del plan gratuito** (decisión de Raúl): Publer Free admite 10 publicaciones programadas a la vez por cuenta. Se cuenta por nombre de cuenta (sin mayúsculas) y red, sumando marcas. Con 10 no se crea el encargo: queda en espera, con el motivo visible, hasta que salga alguna. Constante configurable, `socialhub.publer.scheduled-limit`, 10 por defecto. Técnica: solo se cuentan las que SocialHub tiene programadas en Publer (y los encargos en curso); no ve lo que Raúl programe a mano en Publer.
- **Recomendación proactiva** (decisión de Raúl, 12 nov): «Indica la cuenta de Publer de {marca} para {red}» sale en cuanto una marca tiene una promoción activa y le falta la cuenta de Publer de Instagram o de TikTok, aunque aún no haya nada retenido; desaparece al guardarla. Sigue saliendo también si hay publicaciones retenidas por eso (la fila lo dice).
- **Retención y otras incidencias** (revisión): «Retenida: …» solo se anota en la publicación si no tiene otra incidencia; si la tiene, el motivo queda en la tarea (último problema e historial). Al sacarla de «Programada» se quita. Si sigue retenida por el mismo motivo, el motor no guarda ni avisa a la interfaz. Las canceladas que siguen en Publer (aviso «Estaba programada en Publer») cuentan para el límite de 10 hasta que Raúl las borre allí.
- **«Publer lleno» y «Pasar a Publora»** (decisión de Raúl, 12 nov): cuando una cuenta de Publer tiene publicaciones retenidas por el límite, «Necesita atención» muestra «La cuenta «{cuenta}» de Publer está llena: N publicaciones en espera» (suma las marcas que la comparten). El botón «Pasar a Publora» manda ya a Publora las retenidas por el límite de esa cuenta, sin esperar a las 8 h; solo pasan las de marcas y redes con Publora configurada. Si ninguna la tiene, no hay botón y la fila dice «Publora no está configurada para {marca}/{red}». Tras pulsar, cada una sigue el flujo normal de Publora (margen de 6 h; si ya no llega, lo habitual). La fila desaparece cuando ninguna está retenida por el límite. Técnica: `POST /api/publer-accounts/move-to-publora` (`account_name`, `platform`) devuelve `moved`; la alerta lleva `action`.
## Fase 12 · Instalador de macOS (12 nov 2026)

- **Versión 1.0.0 en todos los sistemas** (decisión de Raúl, 12 nov): jpackage en macOS rechaza una versión que empieza por 0 («The first number in an app-version cannot be zero or negative»), así que SocialHub pasa a la 1.0.0 en Windows y macOS. Se cambia en `backend/pom.xml`, `frontend/package.json` y su `package-lock.json`, y en el valor por defecto y el aviso de `instaladores.yml`. El contrato (`contracts/openapi.yaml`) ya decía 1.0.0.
- **Actualizar desde 0.9.x** (técnica): el `.msi` 1.0.0 usa el mismo `UPGRADE_UUID`, y es de versión mayor, así que sustituye a las 0.9.x sin desinstalar. El workflow lo comprueba: instala el `.msi` de la última release anterior y el nuevo encima, y exige que quede solo SocialHub 1.0.0.
- **`.dmg`** (OK de Raúl): con PostgreSQL y FFmpeg de macOS dentro (perfiles del pom), probado solo en la máquina macOS de GitHub: se monta, se copia la app, se abre, se espera a `http://127.0.0.1:8686`, se comprueba PostgreSQL y se cierra. El job falla si no arranca. Datos y registro en `~/Library/Application Support/SocialHub` (ya definido en `AppPaths`).
## Fase 12 · Bloque 4: API oficial de Instagram (12 nov 2026)

Rama `fase-12-instagram`. Plan aprobado por Raúl; lo técnico lo decidió Claude y se indica.

- **Inicio de sesión unificado** (decisión de Raúl): Meta Graph API con `graph.facebook.com`, sobre el porfolio «El DeLito y Litoques». Versión fija y configurable (`socialhub.instagram.version`, por defecto `v23.0`). Permisos `instagram_basic` e `instagram_content_publish`; sin `business_management`, así que no se pueden listar las cuentas.
- **Clave de «usuario del sistema»** (decisión de Raúl): no caduca y vale para las dos cuentas. La pega Raúl en «Agentes y herramientas › Instagram · API oficial». Se comprueba con Meta (`GET /me`) antes de guardarla y vive en `<datos>/secretos/instagram-api.key`, como la de Publora: nunca en la base de datos, en Drive, en el repositorio ni en los registros (técnico: va en la cabecera `Authorization`, no en la URL).
- **Cuenta de cada marca** (decisión de Raúl): la marca guarda el identificador de su cuenta de Instagram (número largo). SocialHub lo valida con `GET /{ig-id}?fields=id,username` y enseña el @usuario. El botón «Comprobar clave» llama a `/me` y a `content_publishing_limit` de cada marca configurada. El estado sale en «Agentes y herramientas».
- **Publicar a la hora** (decisión de Raúl): Meta no programa. Al programarla solo se comprueba que se puede; a la hora SocialHub crea el contenedor, espera `FINISHED` (con reintentos y tope) y llama a `media_publish`. Imagen, carrusel (hasta 20), reel e historia. Respeta el límite de 100 publicaciones en 24 h antes de publicar. «Publicada» solo llega con el id de Meta (`external_id`) y su enlace (`permalink`); si Meta da el id pero no el enlace, queda «Comprobando» hasta tenerlo.
- **Vídeos y reels** (decisión de Raúl): se suben desde el PC con subida resumible (`upload_type=resumable` y `POST rupload.facebook.com/ig-api-upload/{versión}/{contenedor}`, con `Authorization: OAuth`, `offset` y `file_size`).
- **Imágenes: carpeta pública de Drive** (decisión de Raúl, no túnel): Instagram solo descarga imágenes de una URL pública. SocialHub copia el JPEG (convierte PNG con el JDK; WebP no) a `publico/{id-aleatorio}.jpg`, dentro de la carpeta «Encargos» de la marca (técnico: la carpeta exacta se puede cambiar con `socialhub.publishing.public-folder`). Raúl comparte `publico` a mano («cualquier persona con el enlace»). **El enlace lo obtiene Claude en la nube** con su conector de Drive, con el encargo nuevo `socialhub.public-link` (ver `docs/tarea-programada-encargos.md`). Todo va detrás del puerto `PublicMediaHost`. SocialHub borra los archivos al publicar o descartar.
- **Respaldo: Publer** (decisión de Raúl): «API oficial» va primero cuando está configurada (clave y cuenta de la marca) y el tipo lo permite; si no, Publer, como hasta ahora. TikTok no cambia. Si la API oficial falla **antes de la hora** (en el corte previo: clave inválida, cuenta sin configurar, archivos que faltan, enlace que ya no es imagen, enlaces que no llegan o encargo de enlaces con retraso, necesita ayuda o fallido), la publicación pasa a Publer con el motivo en el historial y en «Necesita atención» («API oficial: …»).
- **Nunca dos veces** (decisión de Raúl): si hay duda de si Meta llegó a publicar (tiempo agotado en `media_publish`, o SocialHub se cierra mientras publica), no se reintenta ni pasa a Publer: queda en «Error de publicación» con «Revisar antes de reintentar». Técnico: antes de publicar se deja una marca en la tarea (`publish_started_at`); si hay marca y no resultado, hay duda.
- **Corte previo** (técnico, revisión): en vez de comprobar solo al programar, SocialHub vuelve a comprobar **todo** (clave, cuenta de la marca, archivos y, en imágenes, enlaces ya llegados y verificados) en el corte previo, que es el último momento en que Publer aún llega: normalmente 9 h antes, y antes si el límite de Publer (8 h antes) cae de noche, para que Publer tenga al menos una pasada de Claude con Chrome (8:40-22:40, a y 40) antes de su límite (`PublerRoute.handoverBy`). Si algo falla en el corte, Publer con el motivo. El encargo `socialhub.public-link` tiene como límite ese corte; si va con retraso (`ClaudePickups.overdue`) o vuelve «necesita ayuda» o «fallido», se pasa a Publer en el acto sin esperar al corte. Los enlaces que lleguen tarde se descartan. No cuenta en el límite diario.
- **Cierre de SocialHub** (decisión de Raúl, 12 nov): si SocialHub está cerrado a la hora de una publicación por la API oficial, la publica **en cuanto se abre, sin límite de tiempo** (aunque sea al día siguiente) y avisa en «Necesita atención» de cuánto retraso tuvo («Publicada con 3 h 20 min de retraso»). Ya no hay LEEME por estar cerrado ni abandono a los 10 min. Las 24 h de la comprobación del enlace cuentan desde que se publicó de verdad, no desde la hora prevista. Los tiempos se comparan con el inicio de la vuelta del motor, para que una subida larga de un reel no afecte a otro a la misma hora.
- **Orden de vías** (técnico): la API oficial pasa a ser la primera en el motor (antes de Publer y Publora) y solo se aplica a Instagram. La migración V23 traduce el índice de vía de los trabajos en curso por el nombre de su vía (probado con Flyway), así que no cambian de vía al actualizar.
- **Después del corte** (técnico, revisión): un fallo a la hora (límite diario, contenedor `ERROR`, enlace que ya no es imagen, clave rechazada) ya no puede ir a Publer (necesita 8 h): «Error de publicación» con el motivo, en «Necesita atención», y un LEEME con el texto «la hora ya pasó: publícala tú cuanto antes o cámbiale la fecha».
- **Duda** (técnico, revisión): ante cualquier error de `media_publish` (también un 4xx) se consulta el estado del contenedor: `FINISHED` es un fallo seguro; `PUBLISHED`, otro estado o no poder consultarlo es duda. Si Meta ya dio el identificador, nunca se marca error por falta de enlace o de comprobación: es duda («Meta la publicó con el identificador X; mira Instagram»). «Necesita atención» enseña el aviso entero de las dudas y los motivos de la API oficial.
- **Enlaces de Drive** (técnico, revisión): Claude solo devuelve el id de Drive (`[A-Za-z0-9_-]{10,}`) y SocialHub construye el enlace `https://drive.google.com/uc?export=download&id={id}`; no acepta URL de Claude. El enlace base solo se cambia con un ajuste para pruebas. Si se deshace la transacción o falla el encargo, se borra lo dejado en `publico`.
- **Errores de Meta en español** (técnico, revisión): se pide `locale=es_ES` y se traducen los códigos más comunes (clave, permisos, límites, formato, enlaces); el texto original de Meta queda solo en el registro.
- **Comprobado con Meta real** (12 nov): la cuota es `config.quota_total` = 100 con `quota_duration` = 86400, y Meta acepta los enlaces de Drive `https://drive.google.com/uc?export=download&id=…` (contenedor de imagen en `FINISHED`). Sigue sin probarse una publicación completa.
- **Sin librerías nuevas** (técnico): `HttpClient` de Java, como Publora.

## Fase 12 · Bloque 3: métricas reales de la promoción (12 nov 2026)

Rama `fase-12-metricas`. Respuestas de Raúl a las dudas de métricas y decisión técnica.

- **Las seis cifras** (decisión de Raúl): reproducciones, alcance, me gusta, comentarios, guardados y compartidos. Se quitan de la pestaña «Impresiones», «Interacciones», «Seguidores ganados» y «Clics en el enlace».
- **Totales y comparación por formato y red** (decisión de Raúl): la pestaña «Métricas» de la promoción enseña los totales por red y, debajo, la comparación por formato y red.
- **Mínimo 5 publicaciones** (decisión de Raúl): un grupo (formato y red) con menos de 5 publicaciones medidas se marca «pocos datos»; la cifra se ve igual.
- **Solo a los 7 días** (decisión de Raúl): la métrica se lee una vez, a los 7 días de publicar, sin lecturas intermedias. Sin medir, la pestaña dice «Las métricas se leen a los 7 días de publicar» y cuántas esperan.
- **Redes separadas con total** (decisión de Raúl): columnas Instagram y TikTok y una columna Total.
- **Claude con Chrome** (decisión de Raúl): las cifras las lee Claude con Chrome (`PublicationMetrics`, sin cambios); SocialHub no llama a ninguna API de métricas.
- **Comparación por medias por publicación** (técnica): en la comparación por formato y red se usa la media por publicación (no la suma), para que grupos de distinto tamaño sean comparables. La media y la suma solo cuentan las publicaciones que tienen esa cifra; si ninguna la tiene, queda «Sin dato» (nunca cero). Solo cuentan las publicaciones con métrica leída (DONE); las publicadas sin métrica DONE salen como pendientes.
- **API** (técnica): `GET /api/promotions/{id}/metrics` devuelve `measured`, `pending`, `by_network` (INSTAGRAM, TIKTOK y una última fila TOTAL, con la suma) y `by_format` (formato, red, publicaciones, `low_sample` y la media). No hay migración nueva: usa la tabla `publication_metric` (V20).

## Fase 12 · Cierre (12 nov 2026)

- **Repositorio público** (respuesta de Raúl): el 12 nov el repositorio pasó a ser público porque GitHub Actions se había quedado sin minutos. Raúl acepta que se vean su correo en los commits y el nombre del PC. Los secretos siguen fuera del repositorio.
- **Versión 1.0.0 en todos los sistemas** (respuesta de Raúl): el instalador de macOS (#37) sale ya con la 1.0.0, como el de Windows.
- **Pruebas con reloj fijo** (técnico, #39): las pruebas que fallaban según la fecha usan un reloj fijo (`PinnedClock`).
- **Tareas programadas** (respuesta de Raúl, 12 nov): con su OK se actualizaron «Encargos de SocialHub» (encargo `socialhub.public-link`) y «Publicar con Chrome» (cuenta de Publer del encargo, opciones de TikTok, no programar si falta la cuenta).
- **Carpeta `publico` de El DeLito** (pendiente): El DeLito aún no tiene carpetas en Drive; al crearlas habrá que crear y compartir su `publico`.


## Fase 12 · Ritmo de Claude (12 nov 2026)

Rama `fase-12-ritmo-claude`, versión 1.1.0. Decisiones de Raúl del 12 nov y decisiones técnicas que salen de ellas.

- **Spec por lotes, no por semana** (decisión de Raúl): por promoción, SocialHub pide el spec en cuanto hay **3 o más publicaciones pendientes de contenido** (en «En producción», sin piezas y sin estar ya en un spec pedido o en curso) **o** cuando la más próxima de las pendientes sale en **menos de 3 días** (aunque sean 1 o 2). El spec lleva solo esas publicaciones y sus fechas (`inputs.publications`); ya no hay `inputs.week` ni domingo. Una publicación no entra en dos specs abiertos; si el spec falla del todo o se cancela, sus publicaciones vuelven a contar. Sin publicaciones no se pide (tampoco a mano).
- **Pasadas de Claude cada 5 horas** (decisión de Raúl): nube «Encargos de SocialHub» a las 00:30, 05:30, 10:30, 15:30 y 20:30 y PC «Publicar con Chrome» 20 minutos después (00:50, 05:50, 10:50, 15:50 y 20:50), `Europe/Madrid`. El PC no se apaga nunca. Las horas están en un solo sitio, `ClaudePasses` (antes, `ClaudePickups` y `PcSchedule`); de ahí cuelgan los retrasos (`overdue`, `PcSchedule.late`), el cálculo de «llegarán sobre las…», el corte de Publer (`PublerRoute.handoverBy`) y el de los enlaces de Instagram.
- **Preferencia nocturna** (decisión de Raúl): las pasadas de 10:30 y 15:30 (y 10:50 y 15:50 del PC) solo hacen los encargos **urgentes**: los que vencerían antes de la siguiente pasada de noche. Las de 20:30, 00:30 y 05:30 (y 20:50, 00:50, 05:50) hacen todo. Regla para las dos tareas: «si la hora actual está entre 08:00 y 18:00 en Madrid, haz solo los encargos con urgent=true; los demás déjalos pendientes».
- **Campo `urgent` del encargo** (técnico): `contracts/task-request.v1.schema.json` gana `urgent` (booleano, opcional): **compatible, sigue siendo la v1** (los encargos antiguos no lo llevan y es `false`). Se calcula al escribir el encargo: `true` si tiene «deadline» y la siguiente pasada completa (de las que lo hacen todo) más 10 minutos de margen cae después del límite; sin «deadline» es `false`. Mientras el encargo sigue en Drive, un recálculo cada 5 minutos (`AiUrgency`) reescribe el archivo si pasa a `true` (nunca baja). La base de datos guarda la marca original (migración V25, `ai_request.urgent`) para saber qué pasada le tocaba.
- **Qué es urgente** (decisión de Raúl, 12 nov, sustituye a la regla de solo «deadline»): un encargo es `urgent=true` si (1) **lo pidió Raúl a mano** desde la interfaz («Pedir textos», «Pedir propuestas», investigación, spec a mano, «Volver a ejecutar», reintento que pulsa él): se hace en la siguiente pasada, aunque sea de día; lo que pide SocialHub por su cuenta (propuestas semanales, balance, spec por lotes, textos y revisión automáticos, publicación) no cuenta como a mano, ni el reintento automático; (2) tiene «deadline» y la siguiente pasada completa más 10 minutos cae después (solo «Programar en Publer» y «Enlaces públicos de imágenes»); o (3) es de **contenido** (spec, textos o revisión de calidad) y alguna de sus publicaciones sale en menos de **48 h** desde que Claude puede verlo (ahora + 30 s de envío + 5 min de Drive). El margen se cambia con la propiedad `socialhub.ai.near-content-hours` (48 por defecto). `AiUrgency` sube a urgentes los de contenido pendientes cuando una publicación entra en esas 48 h. El resto espera a la pasada de la noche: textos, propuestas y balance automáticos, comprobaciones, calendario y métricas. Técnicamente: columna `task.manual` (V26) y regla en `AiUrgentRule`.
- **Publer y Instagram con pasadas cada 5 h** (técnico): Publer necesita el encargo antes de 8 h; SocialHub **no lo pide** si ninguna pasada del PC que lo haga cae, con 10 minutos de margen, antes de ese límite (la publicación sigue por otra vía o queda el LEEME). El corte previo (`handoverBy`) sigue dejando una pasada del PC antes del límite de Publer, pero ahora es normalmente más temprano: la última pasada anterior al límite puede estar hasta 5 h antes. Los enlaces públicos de Instagram necesitan una pasada **de la nube** antes de ese corte (que es 30 minutos antes de la del PC, y la de la nube va 20 minutos antes que la del PC): con imágenes hay que programar con bastante más antelación: el plazo mínimo por la API oficial es de **12,6 h a 18,5 h** según la hora de publicación (también en el MANUAL, §6) para que la API oficial llegue; si ninguna pasada llega, ni se pide y se sigue por Publer.
- **Qué cuenta como pendiente** (técnico): «En producción», sin ningún archivo, con la hora de publicación por delante (una ya pasada no se pide) y fuera de cualquier spec abierto (en cola, en curso, en espera, bloqueado o fallido con reintentos) **o ya completado**. Un spec completado sigue cubriendo sus publicaciones aunque Claude se haya dejado alguna: sin eso, hasta que se generan las piezas (otro encargo) se pediría un spec duplicado, o uno nuevo en bucle. **Un spec completado deja de cubrir** una publicación cuando la generación de sus piezas ha terminado (la tarea de piezas anotada en su resultado; si no consta, pasadas 24 h) y la publicación sigue sin piezas (también si vuelve de «Borrador» a «En producción» sin ellas): vuelve a contar como pendiente y entra sola en el siguiente spec con las reglas del lote. Para evitar bucles, como mucho **2 re-pedidos automáticos** por publicación (3 specs completados con ella); después, solo a mano. Si una publicación en producción sin piezas y fuera de cualquier spec abierto sale en menos de 3 días, sale la fila «{título} no tiene contenido» en «Qué hacer ahora», con el botón «Pedir contenido ya» (un spec a mano, urgente, de esa publicación o de las de su promoción en la misma situación). **Las promociones planificadas** piden su spec con las mismas reglas que las activas, sin esperar a que se activen. Un fallo definitivo (sin reintentos) o una cancelación liberan, pero ese mismo lote no se pide solo otra vez hasta pasadas 24 h (a mano sí). Los specs antiguos, con una fecha como dato, cubren las publicaciones de esa semana (de lunes a domingo). Las altas de spec de una promoción se serializan con un cerrojo de la base de datos (a mano y automático no se cruzan), las piezas de un resultado que no son de las publicaciones del encargo se descartan con aviso y un encargo cuyas publicaciones están todas canceladas se cancela en vez de enviarse.
- **Spec a mano** (técnico): sin datos o solo con una fecha, lleva las pendientes de ese momento; con identificadores de publicación, esas (que no estén ya en un spec abierto). Los datos de entrada del encargo pasan a ser los identificadores de las publicaciones. El archivo del spec se llama `spec-<día de creación>.json`.
- **`socialhub.weekly-spec` y `WEEKLY_SPEC` se quedan con su nombre** (técnico): cambiarlo rompería la compatibilidad con encargos ya guardados y con la tarea programada. Solo cambia la etiqueta («spec.json por lotes»). `promo-spec.v1` no cambia.
- **«Especificación en espera» desaparece** (técnico): la fila SPEC_WAITING de «Qué hacer ahora» avisaba de que faltaban publicaciones aprobadas de la semana; con lotes ya no tiene sentido y no se calcula. El valor sigue en la API por compatibilidad.
- **Tareas programadas** (respuesta de Raúl): no se tocan desde la sesión de programación. La sesión principal las actualiza cuando Raúl instale la 1.1.0; los textos están en `docs/tarea-programada-encargos.md`.
- **Sin librerías nuevas.**

## Fase 12 · FFmpeg probado en macOS (12 nov 2026)

- **Causa del «Permission denied»** (técnico): no era un fallo de permisos del código. El paso informativo de la prueba ejecutaba el binario en cuanto aparecía en `runtime/javacpp`, mientras SocialHub aún lo estaba extrayendo; `FfmpegTool.executable()` le pone el permiso de ejecución justo después de extraerlo (y también a `ffprobe`). Con la comprobación del arranque ya terminada, el binario queda `-rwxr--r--` y funciona. No hizo falta cambiar código ni quitar cuarentena (lo extrae el propio proceso, sin atributo de cuarentena).
- **Prueba obligatoria en el `.dmg`** (técnico): con la app instalada y arrancada, el job pasa a modo real, espera la tarea `check-local-tools` del arranque, la relanza por la API (`RERUN`, o `RETRY` si falló) y exige «OK · FFmpeg» en su registro. Después genera con el FFmpeg que SocialHub dejó listo una imagen y un audio de prueba y de ellos un vídeo H.264/AAC de 2 s; comprueba con ffprobe que dura más de 1 s. Si algo falla, el job falla.
- **Resultado:** FFmpeg 8.1.2 (macOS arm64, GPL, con H.264) funciona en la máquina macOS de GitHub; pieza de 2,0 s. Sigue sin probarse en un Mac real de Raúl (no tiene).

## Fase 12 · Usabilidad (12 nov 2026)

Decisiones de Raúl:
- **Carpetas desde una raíz:** en la ficha de la marca, un campo «Carpeta raíz». SocialHub crea en el disco local (sincronizado con Drive) la estructura y rellena los 7 papeles: Material es la propia raíz; `borradores`, `generados`, `aprobados`, `publicados`, `informes` y `encargos`; dentro de `encargos`: `para-claude`, `para-claude-pc`, `de-claude` y `publico`. Lo que ya existe se respeta (nunca se borra ni se mueve nada). Los 7 campos siguen visibles y editables (avanzado). Si la raíz no es accesible, error claro. Sustituye a la «carpeta base» de la decisión del 8 nov (subcarpetas con mayúscula y solo en papeles vacíos).
- **Avisos en la ficha de la marca:** arriba, con lo que falta por completar y un botón que baja a cada tarjeta: identidad (tono y público), carpetas sin configurar o no disponibles, cuenta de Publer de Instagram y de TikTok, cuenta de Instagram de la API oficial (solo con clave de Meta) y compartir la carpeta «publico» (con la API oficial configurada en la marca). SocialHub no puede comprobar el permiso de Drive: «Ya está compartida» lo oculta, se guarda por marca y vuelve si cambia la ruta de Encargos. Sin pendientes, no sale el bloque.
- **Mapa:** no se puede arrastrar ni alejar a zonas vacías. El contenido siempre queda a la vista (sus bordes no pasan del centro) y el zoom mínimo es el de «Encuadrar todo».
- **Importar la identidad desde un .txt** (formato versión 1, en MANUAL.md y `docs/ejemplos/identidad-marca.txt`): campos `## Nombre`, listas con `- `, comentarios `###`. Antes de guardar se enseñan los cambios (antes → después); lo que viene sustituye y lo que no viene se queda. Errores claros (campo desconocido, límites del contrato). «Copiar plantilla» copia una instrucción para una IA más la plantilla con los límites y, si la hay, la identidad actual.

Decisiones técnicas:
- **Raíz con los 7 papeles:** al dar una raíz explícita, los 7 papeles pasan a apuntar a ella (se sustituyen las rutas guardadas; nada en disco se mueve). La raíz no se guarda aparte: la ficha la deduce (Material con Encargos en `encargos` debajo). «Accesible» = existe la unidad de la ruta y, si la ruta existe, es una carpeta.
- **Marca «publico compartida»:** se guarda en `app_setting` (clave `public-share:<marca>`, valor = carpeta de Encargos con la que se marcó), sin migración nueva. Endpoint `/api/brands/{brand_id}/public-share`.
- **Los avisos se calculan en el frontal** con los endpoints que ya existen (sin endpoint nuevo de «pendientes»).
- **Límites del mapa:** se calculan en el frontal (`viewLimits.ts`). El encuadre y el zoom mínimo usan la caja del contenido a nivel de detalle 1; el desplazamiento se limita con la caja del nivel que se ve (ver «Límites del mapa por niveles de detalle», fase 12).

## Fase 12 · Usabilidad 2 (12 nov 2026)

Peticiones de Raúl, 12 nov:
- **Importar identidad en español:** el selector nativo («Choose File / No file chosen») se sustituye por un botón «Elegir archivo .txt» (el input file queda oculto) y, al lado, el nombre del archivo elegido o «Ningún archivo elegido».
- **Vista previa de la importación en tabla:** cabeceras «Campo», «Ahora» y «Después de importar», con la letra normal de la interfaz (no monoespaciada); las listas, un elemento por línea.
- **Cada aviso, en un solo sitio:** en la ficha de una marca, lo que ya sale en «Te falta por completar» no se repite en «Qué hacer ahora». Fuera de la ficha (mapa, otras pantallas) siguen saliendo como antes.

Decisiones técnicas:
- **Filtro en el frontal** (`withoutBrandNotices`, en `Recommendations.tsx`), sin cambiar el backend ni el contrato. Con la ficha de una marca abierta, se quitan de la franja solo las recomendaciones de esa marca que duplican un aviso: `BRAND_FOLDERS_MISSING` (carpetas) y `PUBLER_ACCOUNT_MISSING` de Instagram y TikTok (las únicas redes que avisa la ficha). Identidad, Instagram API y «publico» no tienen recomendación, así que no hay nada que quitar. Se quedan en la franja las propuestas retenidas, Publora (la ficha no la avisa arriba), las de otras marcas y las generales.

Más decisiones de Raúl (12 nov, mismas peticiones):
- **Listas de «Marcas y promociones»:** punto de color pequeño delante del nombre (en la barra ancha se estiraba por una regla de flex), nombre en grande y debajo la cuenta (@…); en promociones, el punto lleva el color de su marca y debajo van la marca y las fechas.
- **Ficha de la marca:** bajo el título, en pequeño, «Identificador: el-delito».
- **Mapa, zoom semántico:** al alejar (nivel 1, por debajo del 72 %) las estaciones muestran solo nombre, estado y número de tareas y avisos (bloqueadas + con error); la promoción, solo marca, estado, nombre, día y el botón; «Necesita atención», solo el número y el primer aviso (sin botones); «Agentes y herramientas», solo los nombres. El detalle completo sale al acercarse. En ese nivel todo el texto mide 42-44 px del lienzo, de modo que al encuadrar mide al menos 12 px en pantalla (comprobado: 1366x768 encuadra al 30 % y 1600x900 al 45 %, sin ningún texto por debajo de 12 px). Las reglas del nivel 1 se limitan al lienzo (`.world`) para que no cambien las fichas que se abren sobre el mapa.

Decisión técnica: el encuadre de 1366x768 queda en el 30 % y no en el 41 %, porque la franja «Qué hacer ahora» le quita altura al mapa; el tamaño de letra se calcula para ese caso, que es el peor.

## Fase 12 · Arquitectura (12 nov 2026)

Decisión de Raúl:
- **Aplicar la auditoría de arquitectura completa (opción c).** Esta PR protege las fronteras con pruebas (reglas de ArchUnit y de Spring Modulith). El paso de `ai`, `pieces`, `tools` y `operations` a hexagonal, los identificadores con tipo y los puertos primarios como interfaces van en la PR siguiente.

Decisiones técnicas:
- Las reglas nuevas que fallen solo por `ai`, `pieces`, `tools` u `operations` se limitan de momento a los módulos hexagonales, con un comentario que lo dice; no se congelan con `FreezingArchRule`.
- **Reglas del dominio** (`ModularityTest`): `dominioSinFrameworks` y `dominioSoloMiraAlDominio`. Los `package-info` quedan fuera de las reglas porque llevan `@NamedInterface` de Modulith. Los eventos de producción pasan a `production.domain.events` (interfaz con nombre `events`); `BrandIdentity` y `EditorialDecision` a `promotion.domain`.
- **`PromotionQueries` no devuelve tipos de `promotion.domain` a otros módulos:** `requirePromotion`, `identityForAi`, `QualityRules` y `DecisionRef`; la vista completa del anillo usa `PromotionView` (adaptador web) con los DTO.
- **ArchUnit 1.5.1 en `dependencyManagement`** y `spring-modulith-api` en lugar de `starter-core`: ArchUnit y el análisis de Modulith ya no van en el jar (118,0 MB a 113,3 MB).
- **`allowedDependencies` en cada módulo** (también `demo`, que no tenía `package-info`). `platform` deja de ser abierto: interfaces con nombre `live`, `observability`, `postgres`, `process` y `desktop`, y `@Modulithic(sharedModules = "platform")`.
- **Documentación de Modulith:** `spring-modulith-apt` en el compilador, prueba `writesModuleDocumentation` y artefacto `modulith-docs-*` en `backend.yml`.
- **Reglas generales:** sin salida estándar, sin inyección por campo, sin `java.util.logging` (regla propia: la de `GeneralCodingRules` no detectó una llamada en la sonda) y la hora solo del `Clock` (`PostgresArchive` lo recibe inyectado).
- **Eventos sin oyente:** se quitan `AiResultApplied` y `TaskCompleted`. El ciclo de beans se corta en `AiHandlers` (busca los manejadores la primera vez que se usan) y desaparecen los `@Lazy` de publicaciones.
- **Pruebas por módulo:** `ProductionModuleTest` (publica `ReviewApproved` al aprobar) y `PublishingModuleTest` (aprobación, aviso de calidad y paso a manual con `Scenario`). Las propiedades van en `@TestPropertySource`, porque `@ApplicationModuleTest` no tiene `properties`.
- **`verify()` ya ejecuta las reglas DDD de jMolecules** (sonda: un agregado que referencia a otro rompe `modulesRespectTheirBoundaries`), así que se quita el `@ArchTest` de DDD duplicado; el hexagonal se deja.
- Ninguna regla nueva falla solo por `ai`, `pieces`, `tools` u `operations`: no ha hecho falta limitar ninguna a los módulos hexagonales.

## Fase 12 · Arquitectura · PR2: ai, pieces, tools y operations a hexagonal (16 nov 2026)

Decisiones técnicas (sin cambios de comportamiento ni de contrato HTTP):
- **Estructura:** `domain` (modelo y puertos secundarios `@SecondaryPort`), `application` (`@Application`, implementa los puertos primarios), `adapter.in` (web, programación, arranque, observación) y `adapter.out` (persistencia, ficheros, ffmpeg, imágenes). La raíz del módulo es su API pública: interfaces `@PrimaryPort` y los SPI (`AiTypeHandler`, `AiContextProvider`).
- **Los `@ApplicationModule` estaban dentro de comentarios javadoc** en `ai`, `pieces`, `tools`, `operations` y `factory`, así que no se aplicaban. Ahora son reales y `allowedDependencies` declara lo que de verdad se usa (`ai::domain` es interfaz con nombre para `pieces`, `publishing` y `factory`).
- **Identificador tipado** `AiRequestId` en el agregado `AiRequest`. El resto se queda como `String` donde el identificador viene del contrato o de otro módulo.
- **Los relojes (`@Scheduled`) de `ai`, `pieces` y `operations` son adaptadores de entrada** (`adapter.in.scheduling`) que llaman a un puerto primario (`AiRounds`, etc.), con las mismas propiedades y valores por defecto.
- **Generación de piezas en `application`:** un adaptador de salida no puede usar el puerto primario de otro módulo (`Ffmpeg`), así que `PieceGenerator` y `Drawing` viven en `pieces.application`; el acceso a ficheros del spec va por el puerto `SpecLoader`.
- **Reglas nuevas en `ModularityTest`:** `aplicacionSinTecnologia`, `entradaSinSalida`, `salidaSinEntrada`, `entidadesSoloEnPersistencia`, `repositoriosJpaSoloEnPersistencia`, `puertosSecundariosFueraDeLaAplicacion`, `puertosPrimariosEnLaRaiz`, `transaccionesFueraDeDominioYEntrada` y `programacionSoloEnAdaptadoresDeEntrada`. `ArchitectureProbesTest` las prueba con clases que las incumplen a propósito (`src/test/java/archprobes`, fuera de `com.socialhub` para que Spring no las recoja): cada regla tiene que fallar nombrando a su sonda.

Excepciones heredadas (módulos antiguos, no convertidos aún). Las dos últimas reglas se aplican por ahora solo a `ai`, `pieces`, `tools` y `operations`; fallan en:
- **Relojes en la raíz del módulo** (pasar a `adapter.in.scheduling` llamando a un método público del servicio): `backup.DailyBackup.run`, `demo.DemoSimulator.tick`, `drive.DriveCheck.check`, `factory.ServicesWatch.check`, `production.TaskEngine.poll` y `requeueWhenFoldersAppear`, `publishing.AutoRequests.run`, `publishing.ExternalCalendar.run`, `publishing.PublicationMetrics.run`, `publishing.PublishingEngine.run`.
- **Transacciones en adaptadores de entrada:** `production.adapter.in.web.ProductionView` y `promotion.adapter.in.web.PromotionView` (`@Transactional(readOnly)` para montar las vistas). Hace falta mover el montaje de la vista a la aplicación, pero la aplicación no puede depender de `api`; hay que decidir cómo (pregunta pendiente para Raúl).
- **Pendiente de decidir:** convertir `promotion`, `production` y `publishing` de servicios concretos `@PrimaryPort` en la raíz a interfaz más `application`, como los cuatro de esta PR.

## Fase 12 · Pruebas e2e con Playwright (`fase-12-playwright`)

- **Las pidió Raúl:** calidad antes que cobertura. 8 recorridos reales (técnica; carpeta `e2e/`): mapa, alta de marca con carpetas, identidad desde .txt, avisos, promoción y propuestas, Agentes y herramientas, Claude y cierre de la app.
- **Modo real, no demo:** arrancan el jar con la web dentro, PostgreSQL incluido en un puerto libre y una carpeta de datos temporal. Los servicios externos se simulan con carpetas temporales: las de Drive son carpetas del disco y Claude lo hace el propio recorrido, que deja en `de-claude` el resultado que dejaría la tarea programada. No se llama a Meta, Publer ni Publora, ni se guardan claves. Solo se adelantan los envíos y lecturas de Claude (`--socialhub.ai.dispatch-millis` y `read-millis` a 1,5 s).
- **Cada recorrido vigila la consola:** un `console.error` o una excepción sin declarar hace fallar la prueba (`allowConsole` para los esperados, como un 409).
- **Un solo arranque para todos, en orden;** el de cierre va el último y comprueba que no queda ni el jar ni PostgreSQL.
- **`e2e.yml` en Linux** (PR con cambios en `e2e`, `frontend`, `backend` o `contracts`): capturas, trazas y registro de la app como artefacto si falla. En local: `cd e2e && npm ci && npm run e2e:full` (o `E2E_PORT=… E2E_REUSE=1` para reutilizar una app ya arrancada).
- **Arreglos que salieron de las pruebas:** `AiSettingsInput.models` admitía 10 tipos y hay 11 (no se podían guardar los ajustes de Claude); y «Cerrar» no enseñaba «SocialHub se ha cerrado» porque la respuesta 202 sin cuerpo se leía como JSON.
- **Fallos de la app anotados:** se arreglaron en la rama `fase-12-fallos-playwright` (sección «Fallos de las pruebas e2e arreglados», más abajo; «límites medidos al nivel de detalle 1» ya lo estaba en la PR #60).

## Fase 12 · Propuestas vacías (15 nov 2026)

Decisión técnica (error visto en el PC de Raúl: la promoción «Primera» empieza en domingo y Claude contestó con `proposals: []` y el motivo en `summary`):
- **Una lista `outputs.proposals` vacía es una respuesta válida.** La tarea se completa con el texto «Sin propuestas: <summary>» (más los avisos de Claude) y no crea nada. Solo es error si el campo falta o no es una lista.
- El aviso es el resultado de la tarea completada, no un error rojo. No se añade recomendación nueva en «Qué hacer ahora».
- **No se vuelve a pedir:** el registro de encargos automáticos (`AiAutoOrderEntity`) guarda la semana pedida, haya salido con propuestas o sin ellas.
- El contrato `task-result.v1` no exigía `minItems`: no cambia.

Decisión de Raúl:
- **La primera ventana de propuestas se extiende hasta la primera fecha de cadencia con margen.** Si una promoción empieza en un día sin cadencia (p. ej. un domingo con cadencia x/v), la ventana de la primera petición no acaba en el domingo: llega hasta su primera fecha de cadencia con al menos 6 h de margen sobre la recogida de Claude. Ejemplo: «Primera» empieza el domingo 15 nov con x/v a las 19:00 y pide del 15 al miércoles 18 nov. Si la fecha de cadencia cae dentro de la semana, no cambia nada; si la promoción termina antes de esa fecha, la ventana no se extiende (sin propuestas es una respuesta válida).

Decisiones técnicas:
- La cadencia es texto libre: se leen los grupos «días + hora» («L/X/V 19:00», «x/v a las 19:00»; L M X J V S D). Si no se entiende, la ventana es la de siempre.
- La ventana extendida viaja como segundo dato de entrada del encargo (el miércoles, `2026-11-18`); el manejador la usa para `inputs.week.to`, lo ya planificado y para aceptar o descartar las propuestas.

## Fase 12 · Versión visible y numeración de instaladores (15 nov 2026)

Problema (diagnosticado con el registro de Raúl): el instalador que generaba la Action desde `main` salía siempre con la versión por defecto del workflow (1.1.0), igual que la ya instalada. Windows reconocía el mismo producto y entraba en «modo mantenimiento» («Skipping FindRelatedProducts: not run in maintenance mode»): no actualizaba nada. Además la web no enseñaba qué versión estaba en marcha.

Decisiones técnicas (la numeración la propuso Raúl; el resto es técnico):
- **Fuente de verdad: `<revision>` en `backend/pom.xml`** (1.2.0). `<version>` es `${revision}` (Maven 3.5+, sin plugins ni librerías nuevas). Las compilaciones de GitHub la sustituyen con `-Drevision=...`, de modo que el jar lleva la misma versión que el instalador y que enseña la web.
- **Numeración:** release (`publicar`) = la versión exacta que se escribe (vacía: la del pom, 1.2.0); cualquier otra compilación (a mano sin publicar, o PR) = `<mayor>.<menor>.<número de ejecución>`, por ejemplo 1.2.57. Crece en cada ejecución (Windows compara los tres números y el MSI admite hasta 65535 en el último). Consecuencia: tras instalar un 1.2.57 de pruebas, una release 1.2.0 sería menor y no actualizaría; al subir de release hay que subir `<revision>` (1.3.0) o publicar con un número mayor.
- **`UPGRADE_UUID` no cambia.**
- **Commit y fecha de compilación sin librerías nuevas:** goal `build-info` del `spring-boot-maven-plugin` (ya en el pom) con la propiedad adicional `commit`, que el workflow pasa con `-Dsocialhub.commit=<7 primeros caracteres del SHA>` (el de la cabeza de la PR, no el del merge). En local sale `local`. No se añade `git-commit-id-plugin`.
- **API:** `GET /api/system/version` (etiqueta `App`) devuelve `version`, `commit` y `build_time`; solo lectura. Sin `build-info.properties` (IDE) devuelve «desconocida».
- **Web:** `1.2.57 · a1b2c3d` en pequeño junto al nombre, con la fecha en el recuadro del ratón y el detalle (versión, commit, fecha de compilación, modo Real/Demo) al pulsar.
- **Workflow de instaladores:** un trabajo `version` calcula la versión y el commit una sola vez y lo usan Windows y macOS. La prueba de actualización exige que la versión nueva sea mayor que la instalada, instala encima de la release anterior, abre SocialHub y comprueba que la web devuelve la versión y el commit nuevos. La instalación limpia de Windows y el `.dmg` comprueban lo mismo. En macOS no hay actualización real: se arrastra la app y solo se comprueba la versión.

## Fase 12 · Pruebas de los flujos de la aplicación (15 nov 2026)

Matriz completa en `docs/pruebas-flujos.md` (14 flujos: los 12 de Raúl más «vida entera de una publicación» y «copia y restauración con datos de negocio»). Decisiones técnicas, sin librerías nuevas:
- **Pruebas de flujo en `com.socialhub.flows`** con `@FlowTest`: SocialHub entero (PostgreSQL real, modo real), reloj propio `FlowClock` (arranca el mar 10 nov 2026 10:00 y se adelanta a mano) y solo lo de fuera simulado: Claude y Drive (archivos en disco), Meta y Publora (servidor HTTP local), Publer (archivo de resultado de Claude). Un contexto de Spring por clase, cerrado al acabar (`@DirtiesContext`), para no dejar PostgreSQL abiertos.
- **Cierre (`/api/app/shutdown`)** se prueba arrancando SocialHub en un proceso aparte (la llamada hace `System.exit`).
- **Los relojes internos (motor, calendario, métricas) se aceleran por clase** con `@TestPropertySource` en lugar de llamar a sus métodos, para probar el flujo por la API como lo vería la interfaz.
- **Fallos de la aplicación encontrados** (no se han tocado; ver la matriz): el resultado de Claude pisa el texto que Raúl ha escrito mientras tanto; un aviso de calidad aceptado no caduca nunca (duda funcional); la incidencia «Publicación manual con LEEME» queda escrita en la publicación ya publicada.

## Fase 12 · «Conectar con Instagram» (OAuth de Meta) y otros sistemas (15 nov 2026)

Rama `fase-12-oauth`. Decisión de Raúl: botón «Conectar con Instagram» que haga la autenticación de Meta, detecte la cuenta de cada marca y guarde las credenciales; la clave de usuario del sistema se mantiene como alternativa. Lo demás es técnico.

- **Flujo (técnico):** Facebook Login con `graph.facebook.com` (la misma versión fija `socialhub.instagram.version`). Permisos mínimos `instagram_basic`, `instagram_content_publish`, `pages_show_list` y `pages_read_engagement`. `POST /api/instagram/oauth/start` crea un `state` de 32 bytes aleatorios, de un solo uso y con 10 minutos de vida; `GET /api/instagram/oauth/callback` lo comprueba **antes** de llamar a Meta (si no vale, no hay ninguna llamada), cambia el `code` por una clave, esta por una de larga duración (`fb_exchange_token`, unos 60 días; 60 si Meta no dice la duración), lista `/me/accounts?fields=name,instagram_business_account{id,username}` y redirige a `/?instagram=ok|error&message=…`.
- **Dirección de redireccionamiento:** `http://localhost:{server.port}/api/instagram/oauth/callback` (8686). Sale en la tarjeta con botón «Copiar» y ayuda «¿Dónde lo saco?» (Meta for Developers › tu app › Inicio de sesión con Facebook › Configuración › «URI de redireccionamiento OAuth válidas»).
- **Secretos:** App ID, App Secret y la conexión (clave + caducidad + @usuarios, en JSON) viven en `<datos>/secretos/` (`instagram-app-id.key`, `instagram-app-secret.key`, `instagram-oauth.key`), igual que la clave de usuario del sistema: nunca en la base de datos, el repositorio ni los registros. El secreto y las claves van en el cuerpo de un POST o en la cabecera `Authorization`, nunca en una URL; el secreto no vuelve a salir por la API (solo `app_secret_saved`). Al guardar App ID y Secret se comprueban con Meta (`client_credentials`).
- **Qué clave usa la publicación (técnico):** la conexión mientras no haya caducado y, si no, la clave de usuario del sistema (`InstagramCredentials.activeToken`). Una conexión caducada no se usa; si no hay clave del sistema, la ruta queda «no configurada» y la publicación sigue por Publer, como sin clave.
- **Autodetección (técnico):** al conectar, cada marca **sin cuenta** recibe la cuenta cuyo @usuario coincide con su `handle` (sin mayúsculas ni @). No se pisa una cuenta que Raúl puso a mano y es otra. En la ficha, un desplegable con las cuentas encontradas (`GET /api/instagram/accounts`) y el campo manual, que sigue disponible.
- **Caducidad (técnico):** recomendación `INSTAGRAM_CONNECTION_EXPIRING` («La conexión con Instagram caduca en N días» / «ha caducado») cuando quedan 7 días o menos, con botón «Renovar conexión» que lleva a la tarjeta (nuevo destino `INSTAGRAM`), donde «Renovar conexión» repite el flujo con un clic. Si hay clave de usuario del sistema, el aviso lo dice. «Agentes y herramientas» enseña «Conectado con Meta · caduca el …» o «caducó el …».
- **Pruebas:** `InstagramOAuthTest` (Meta simulado con HTTP local: state inventado, repetido y caducado, permiso denegado, código malo, intercambio, larga duración, caducidad, autodetección, renovación, clave del sistema, y que ninguna clave sale en la API ni en el registro); `FactoryRecommendationsTest`; frontal con la API simulada. **Playwright con Meta simulado: no hay infraestructura e2e en `main`** (Playwright solo está en el entorno de Claude, no en el repositorio ni en CI), así que no se ha añadido.

### Estudio: «otros sistemas» (¿se puede hacer lo mismo?)

| Sistema | ¿OAuth hace falta? | Qué exige | Conclusión |
|---|---|---|---|
| **Publer** | No: solo clave de API (Ajustes › API y webhooks › Generar clave, con ámbitos) | API **solo en los planes Business y Enterprise**; Raúl tiene el plan Free. [Cómo acceder a la API](https://publer.com/help/en/article/how-to-access-the-publer-api-1w08edo) · [Autenticación](https://publer.com/docs/getting-started/authentication) | No se implementa: exige un plan de pago (prohibido sin autorización expresa). Hoy Publer se maneja con Chrome. Si Raúl decide pagar Business, sería una vía por clave, sin OAuth |
| **Publora** | No: clave de API (ya integrada, `PubloraService`) | La clave de «API» en su panel. [Autenticación](https://docs.publora.com/authentication) (no se pudo abrir en esta sesión; lo confirma la integración que ya funciona) | Ya hecho: no hay OAuth que añadir |
| **TikTok** (Content Posting API) | Sí: OAuth 2.0 con Login Kit | App de TikTok for Developers con los productos Login Kit y Content Posting API y el permiso `video.publish` (o `video.upload` para dejarlo en borradores). **Auditoría de la app**: hasta pasarla, los clientes sin auditar solo pueden publicar con visibilidad privada (`SELF_ONLY`) y como mucho 5 usuarios cada 24 h; la auditoría exige que la app sirva a creadores en general, **no** a uso interno o de pruebas. Redirect URI propia (puede ser `localhost`). [Guía de contenido](https://developers.tiktok.com/doc/content-sharing-guidelines) · [Solicitud de auditoría](https://developers.tiktok.com/application/content-posting-api) · [Publicar directo](https://developers.tiktok.com/doc/content-posting-api-reference-direct-post) | No se implementa: para publicar en público hace falta una auditoría que una herramienta personal difícilmente pasa; sin ella todo sale privado. TikTok sigue en Publer. Se puede reabrir si Raúl quiere probar la solicitud de auditoría |
| **Google Drive** | No hace falta | SocialHub y Claude usan carpetas sincronizadas por Google Drive para escritorio; el enlace público de las imágenes lo obtiene Claude con su conector | Nada que hacer. La API de Drive con OAuth exigiría un proyecto de Google Cloud y pantalla de consentimiento con permisos sensibles, sin aportar nada |

**Conclusión:** no hay ningún otro sistema donde un OAuth sea posible y útil sin requisitos externos (plan de pago o auditoría de TikTok), así que solo se implementa el de Meta. Cada uno queda documentado con sus enlaces.

## Fase 12 · Claridad (15 nov 2026)

Hallazgos de Raúl al usar SocialHub de verdad. Decisiones (técnicas, a partir de lo que pidió Raúl):
- **Editar y «Nueva publicación»:** la página baja suavemente al formulario y pone el foco en su primer campo editable; al «Cerrar» o «Guardar» vuelve a la fila. Con «reducir movimiento» en el sistema el salto es directo.
- **Control de calidad:** explicación dentro del bloque («¿Qué es esto?», qué hace Aceptar, «Comprobar de nuevo» y «Revisar con Claude») y una frase de «qué hacer» por cada comprobación. El cuadro «Archivos» ya no habla de spec.json.
- **Avisos nuevos** (solo en «Qué hacer ahora» y «Te falta por completar»): identidad de la marca incompleta (tono, público o temas que no se tocan; en la ficha, con botón «Importar desde .txt») y publicaciones de camino a publicar sin 6 horas de margen o con la hora ya pasada. Ya existían y no se duplican: cuenta de Publer, cuenta de Instagram para la API oficial y publicaciones en producción sin contenido a menos de 3 días. «Temas que no se tocan» vacío cuenta como identidad incompleta: si una marca de verdad no tiene ninguno, el aviso no se va hasta que se escriba algo.

## Fase 12 · Arreglos de los flujos (15 nov 2026)

Rama `fase-12-arreglos-flujos`. Los puntos 2 y 4 son decisión de Raúl; el resto, técnica.

- **Textos de Claude que pisaban el texto de Raúl (flujo 6, técnica).** `PublishingService.applyTexts` ya no sustituye un texto o unos hashtags de Raúl. Es «de Raúl» lo que existe y no coincide con la última propuesta de Claude sin tocar (`Publication.hasOwnText`: el texto o los hashtags están y son distintos de `aiCaption`/`aiHashtags`). En ese caso el texto de Claude no se aplica: se anota en la incidencia de la publicación («Claude propuso otro texto, que no se ha aplicado porque ya habías escrito el tuyo: «…» #…»), que Raúl ya ve y puede copiar, y el resultado del encargo dice cuántos no se aplicaron. Si Raúl no ha tocado lo de Claude (o lo descartó), un resultado nuevo sí lo sustituye. Sin pantallas ni contrato nuevos. Limitación asumida: no hay «texto desde que se pidió el encargo» guardado, así que pedir a mano «Textos» de una publicación que ya tiene texto de Raúl también deja la propuesta en la incidencia, no la aplica.
- **Aceptar un aviso de calidad caduca (flujo 7, decisión de Raúl).** Cualquier cambio de texto, hashtags o archivos de la publicación (edición, descartar el texto, asociar o quitar un archivo, piezas generadas, texto nuevo de Claude) borra las aceptaciones de esa publicación (`QualityService.invalidateAcceptances`) y los avisos se comprueban de nuevo: reaparecen si siguen fallando. Cambiar otra cosa (título, hora) no las anula; guardar el mismo texto tampoco. Los avisos de la revisión de Claude siguen hasta la siguiente revisión, pero su aceptación también caduca. La ayuda del control de calidad, el aviso junto a cada aceptación y el manual lo dicen.
- **Incidencia «Publicación manual con LEEME» (flujo 9, técnica).** Al confirmar la publicación (`PublishingEngine`, los tres caminos que la dan por publicada) se quita esa incidencia: ya no hay nada que hacer a mano. Las demás incidencias no se tocan.
- **Ronda semanal W+1 tras la ventana ampliada (decisión de Raúl).** Cuando la primera ventana de propuestas se amplía más allá del domingo, se anota hasta dónde llegó (`ai_auto_order` con tipo `FIRST_WINDOW_END` y esa fecha, sin migración) y el encargo de W+1 de esa promoción empieza el día siguiente: lleva un tercer dato de entrada (el primer día) y `WeeklyProposalsHandler.windowStart` lo usa en `inputs.week.from`, en lo ya planificado y para descartar lo anterior. Si la ventana cubre toda W+1, no se pide. La ronda pide antes la primera semana que W+1.
- **Tarjeta «Instagram · API oficial» siempre «en espera» (técnica).** `ServicesWatch.refresh` llama a `InstagramService.check()` una vez cuando hay clave y no hay comprobación vigente (`hasCheck()`, tras reiniciar o guardar clave o cuenta). `check()` captura cualquier fallo y deja siempre su resultado, así que no hay bucle: la siguiente llamada solo ocurre tras otro cambio. Una llamada a Meta por cambio.
- **«Conectar con Instagram» desactivado (técnica).** Mientras falten el App ID y el App Secret se ve junto al botón «Para conectar, primero guarda el App ID y el App Secret de tu app de Meta (arriba).» (y, si hay clave del sistema, «Mientras tanto, SocialHub ya usa la clave de usuario del sistema.»). Si Meta falla al empezar la conexión, el error se pinta en la tarjeta (`role="alert"`), con prueba.

## Fase 12 · Límites del mapa por niveles de detalle (rama `fase-12-limites-mapa`, técnica)

- **Límites del mapa por niveles de detalle.** Los límites se medían siempre con la caja del nivel 1, pero cada nivel tiene tarjetas de otro tamaño (con muchas tareas, lo que se ve a niveles 2 y 3 puede salirse de esa caja; a la inversa, el nivel 1 usa letra grande y su caja es mayor que la del nivel 3), así que no se podía llegar a los bordes o la pantalla quedaba vacía. Ahora hay una caja por nivel: la del nivel 1 sigue sirviendo para «Encuadrar todo», el zoom mínimo y `fitScale` (el encuadre no cambia); el desplazamiento (`clampView`) usa la caja del nivel de detalle que se ve (`boxForLevel`), medida en los tres niveles al encuadrar y al cambiar los datos. Se probó primero la unión de las tres cajas, pero con ella el contenido del nivel 3 (más pequeño que el 1) dejaba la pantalla vacía en el borde. La regla «el borde no pasa del centro» se mantiene: basta para ver entero cada borde. Las cajas ignoran los elementos ocultos en cada nivel. `flyToStation` (y con él las tarjetas de «Qué hacer ahora» y las tareas) no cambia: el límite se aplica en cada fotograma. Se quita el `test.fail` del recorrido «acercar al máximo con el mapa arrastrado al borde» y se añade otro que llega a cada borde con el zoom al máximo.

- **Recorrido e2e «Avisos» que fallaba de lunes a miércoles (técnica; era la prueba, no la app).** La promoción de la prueba empezaba «hace 3 días». Por decisión de Raúl (ver «primera semana»), la primera semana de una promoción es la de su fecha de inicio: si esa semana ya terminó (de lunes a miércoles, 3 días atrás cae en la semana anterior) no se piden propuestas, no hay encargo retenido y no sale «Las propuestas de … no pueden salir». Falló desde la medianoche de Madrid del lunes 16 nov, también en `main`. Ahora las promociones de esa prueba empiezan hoy (`daysFromToday(0)`, hora de España), así que no depende del día de la semana. Pendiente de Raúl, si quiere: una promoción dada de alta con inicio en una semana pasada no recibe propuestas de la semana en curso hasta el lunes a las 18:00 (para W+1); no se ha cambiado.
- **«Encuadrar todo» en los recorridos e2e:** la comparación con el encuadre inicial admite un 5 % de zoom y 25 px de posición (la franja «Qué hacer ahora» cambia el alto del mapa y se actualiza sola), en vez de 0,005 y 2 px.

## Fase 12 · Fallos de las pruebas e2e arreglados (rama `fase-12-fallos-playwright`)

Decisión técnica (los seis fallos que anotó la fase de Playwright; las pruebas e2e ya exigen lo correcto, sin `test.fail` ni tolerancias):
- **Ficha de marca sin conexión de Meta:** la tarjeta de Instagram de la ficha primero pregunta el estado (`GET /api/instagram`) y solo pide la lista de cuentas (`/api/instagram/accounts`) si hay conexión vigente o clave del sistema (`configured`). Sin ellas no se hace la petición, así que no hay 400 en la consola. El contrato y el backend no cambian. Se quitó la tolerancia de `e2e/support/fixtures.ts`.
- **Cierre lento:** al apagarse la app (`ContextClosedEvent`), `LiveUpdates` termina las conexiones de «En directo» (SSE). Tomcat ya no espera a que acaben: con una pestaña abierta, el cierre pasa de 30 s a menos de 2 s. El navegador, al ver cortada la conexión, reintenta, pero la web ya muestra «SocialHub cerrado».
- **Zoom mínimo de la rueda:** no se pudo reproducir el 2 % de diferencia; con la medida por niveles de la PR #60 el mínimo coincide con el encuadre. La prueba ahora lo exige con 0,1 % de margen.

Cambios en la interfaz:
- **Panel lateral y «Agentes y herramientas»:** al abrir una herramienta (desde la lista, el buscador, un aviso o «Qué hacer ahora») el mapa se desplaza, sin cambiar el zoom, lo justo para que la lista quede entera a la vista a la izquierda del panel. Así se puede pasar de una herramienta a otra sin cerrarlo. Si ya se veía entera, no se mueve.
- **Letra del mapa alejado:** los textos del nivel 1 pasan de 42-44 px a 46-48 px del lienzo y el número de avisos de cada estación (`.badge`) de 26 px a 46 px (el círculo, de 44 a 64 px). Con el encuadre típico de 1366x768 (zoom 0,27-0,30) todo mide al menos 12 px en pantalla. La prueba exige 12 px, también para el `.badge`.
- **Tarjetas del centro del anillo con 3 o más promociones** (opción más conservadora, sin rediseñar): «Necesita atención» pasa a la tercera columna y las promociones se reparten en las otras dos, en tantas filas como hagan falta. Con 3 o más, cada tarjeta se compacta para caber en su fila: marca, estado y nombre en el nivel alejado (sin la fase ni el botón), y a más zoom se quitan además el calendario, las barras y los datos; se sigue entrando al centro de control con un clic o Intro. Con 5 o más (3 filas) se quita también el estado. Con 1 o 2 promociones no cambia nada. Más de 6 promociones siguen sin caber del todo (se recortan): haría falta un diseño nuevo (paginar o desplazar), que se decidirá con Raúl si llega a pasar.

## Fase 12 · Identidad: «temas que no se tocan» vacío es válido (rama `fase-12-identidad-vetados`, decisión de Raúl, 16 nov 2026)
- Un campo «temas que no se tocan» (`forbiddenTopics`) vacío significa que no hay temas vetados y **no cuenta como identidad incompleta**. Los huecos de la identidad son solo el tono y el público (`PromotionQueries.identityGaps`, aviso de la ficha en `BrandNotices` y recomendación `IDENTITY_MISSING`).
- Si no queda ningún hueco, el aviso «identidad incompleta» no sale. Textos, contrato, MANUAL y TUTORIAL ajustados; pruebas del backend, del frontal y e2e actualizadas.

## Fase 12 · Pruebas de mutación con PIT (15 nov 2026)

Decisión de Raúl:
- **Calidad de pruebas, no cobertura:** pitest-maven con el plugin JUnit 5 (gratuitos, autorizados). Pendiente de su OK: imponer el umbral del 75 %.

Decisiones técnicas:
- **No va en `verify`:** `mvn -Ppit-unitarias test-compile org.pitest:pitest-maven:mutationCoverage`. Informe HTML y XML en `backend/target/pit-reports`. Se excluyen la API generada (`com.socialhub.api.*`), los `*MapperImpl` y la clase de arranque; `lombok.config` marca el código generado.
- **Solo pruebas unitarias (`-Ppit-unitarias`):** medido con datos, con las pruebas de Spring y PostgreSQL cada mutante que sobrevive a las unitarias reinicia contexto y base de datos. En GitHub (Linux) la pasada completa murió dos veces a los ~47 min (`The runner has received a shutdown signal`) con decenas de minions en `TIMED_OUT` y procesos `postgres` huérfanos. Con el perfil, la pasada completa tarda 12 min en un portátil de 2 CPU. Consecuencia: el código que solo cubren las pruebas de integración cuenta como «sin cobertura», así que el porcentaje es un suelo.
- **Linux, no Windows:** PostgreSQL incluido arranca sin root en el runner de Ubuntu (las pruebas pasan en Linux). Hace falta instalar las bibliotecas del FFmpeg incluido (`libxcb1 libxcb-shm0 libasound2t64 libpulse0 libva2 libva-drm2`); sin ellas fallaban `PieceGeneratorTest` y `TaskEngineTest`.
- **Por PR (`mutacion.yml`):** solo las clases de producción añadidas o modificadas respecto a main (`targetClasses` calculadas del diff, con `*` para las internas). **Informativo:** el umbral sale de la variable del repositorio `PIT_UMBRAL` (vacía = 0). Para imponer el 75 %, Raúl pone `PIT_UMBRAL=75`.
- **Semanal (`mutacion-semanal.yml`):** lunes 03:00 UTC, o a mano; pasada completa sin umbral, informe como artefacto y tabla por módulo en el resumen del trabajo (`.github/scripts/resumen-mutacion.py`). Se prueba también al cambiar su propio archivo.
- **Primera medición (solo unitarias, 4089 mutantes):** ver «Estado» en `docs/estado.md` / PR #51. Total 26 %. Lejos del 75 %: por eso el umbral no se impone de momento.

## Fase 12 · Zoom mínimo del mapa = encuadre vigente (16 nov 2026)

Decisión técnica (fallo intermitente de `01-mapa.spec.ts` «límites de arrastre y de zoom», también en main):
- **Causa:** el zoom mínimo (`minScale()`) se calcula en vivo con el alto disponible, pero el mapa solo se volvía a encuadrar con `hasNext` (recomendaciones sin filtrar) y con `resize` de la ventana con 150 ms. La franja «Qué hacer ahora» también cambia al filtrar avisos de la marca abierta, al desplegar «Ver N más» o al llegar las recomendaciones después de cargar: el alto del mapa cambiaba sin encuadrar. El recorrido guardaba el encuadre de antes (`fitted`) y lo comparaba con un mínimo ya recalculado (≈2,5 % por debajo).
- **Arreglo:** un `ResizeObserver` sobre el mapa (sustituye al efecto de `hasNext` y al de `resize`): al cambiar su tamaño se vuelve a medir y, si la vista estaba en el encuadre (`lastFit`), se vuelve a encuadrar; si no, solo se acota. Así el mínimo y el encuadre salen del mismo alto y la misma caja.
- **Prueba:** el recorrido compara el mínimo alcanzado con la rueda con el de «Encuadrar todo» en ese momento, con tolerancia fija del 0,1 %, sin la comparación con un valor viejo. Prueba unitaria en `viewLimits.test.ts`.

## Fase 12 · Registros de instalación, arranque y uso (rama `fase-12-registros`, técnica; a raíz de una instalación que se quedó a medias)

Problema: a Raúl se le quedó SocialHub sin arrancar tras una instalación a medias (faltaba `socialhub.jar`, Windows pidió reiniciar) y no había ningún registro de la instalación ni del arranque; el único (`socialhub.log`) solo existe con la aplicación ya en marcha.

**Instalador (A)**
- Un `.msi` no registra solo. El workflow publica junto al `.msi` (mismo artefacto y release) `Instalar SocialHub (con registro).cmd` y `Desinstalar SocialHub (con registro).cmd` (fuente en `scripts/windows/instalador/`): `msiexec /l*v` a `%LOCALAPPDATA%\SocialHub\logs\instalacion-<fecha-hora>.log` (y `desinstalacion-…`). Crean la carpeta, localizan el `.msi` por la carpeta del propio `.cmd`, no piden ni escriben contraseñas, y al acabar enseñan el registro y el código de salida (explican 0, 3010, 1603…). El de desinstalar busca el código de producto en el registro de Windows (solo lectura), así no depende de tener el mismo `.msi`. ASCII y CRLF (la CI lo comprueba); `/silencioso` para las pruebas.
- **¿Sin lanzador?** Se valoró la política de Windows Installer `Logging` (`HKLM\Software\Policies\Microsoft\Windows\Installer`, valor `voicewarmupx`): hace que todo `.msi` registre en `%TEMP%\MSI*.LOG`. **No se hace**: exige escribir en `HKLM` (tocar el sistema de Raúl) y afecta a todos los instaladores del equipo. Tampoco se cambia el WiX que genera jpackage (`--resource-dir`) para registrar desde el propio `.msi`: no registraría el caso que importa (instalación que falla) y complica el mantenimiento. Queda como apoyo el Visor de eventos (origen `MsiInstaller`), documentado en el manual.
- La CI ya instalaba con `/l*v`; ahora todos los `msiexec` de la prueba registran (instalar versión anterior, actualizar, instalar, desinstalar) en una carpeta y se sube como artefacto `registros-instalacion-windows` aunque falle (con los registros de SocialHub y los eventos `MsiInstaller`). El workflow se prueba solo al cambiarse en una PR, que es como se prueba esto. Además prueba los dos `.cmd` (instalan y desinstalan de verdad, la carpeta de registros se crea sola y los registros tienen contenido).
- Documentado en `MANUAL.md` y `docs/TUTORIAL.md`: cómo instalar con registro, dónde mirar y que, si Windows pide reiniciar, primero se cierran SocialHub y Chrome.

**Ejecutable (B)**
- Registros en `%LOCALAPPDATA%\SocialHub\logs` (macOS: `~/Library/Application Support/SocialHub/logs`). En macOS no se pasa `-XX:ErrorFile` (la ruta lleva un espacio y el lanzador parte las opciones por los espacios): el informe de fallo fatal queda en la carpeta temporal y se recoge al abrir.
- `arranque.log` (nuevo, escrito a mano sin Spring ni Logback: `platform/diagnostics/StartupLog`): versión, SO, Java, carpeta de datos, opciones de la JVM, PostgreSQL arrancando y en marcha, Flyway por base de datos, puerto y «listo»; si el arranque falla, la traza. Recoge también la salida estándar y de error de la JVM (en la aplicación instalada no hay consola), línea a línea y sin secretos. Solo se activa con `-Dsocialhub.desktop=true` (no en desarrollo ni en pruebas). Se aparta a `arranque.1.log` si pasa de 2 MB. La consola de Spring pasa a nivel WARN en el modo instalado para no duplicar `socialhub.log`. Se apaga el banner.
- **`--win-console` no**: abriría una ventana negra en cada arranque. En su lugar, la redirección se hace desde Java en `main` (antes de nada).
- **Fallos fatales de la JVM:** `-XX:ErrorFile=$LOCALAPPDATA\SocialHub\logs\hs_err_pid%p.log` en las opciones de jpackage. El lanzador de jpackage expande variables de entorno `$VAR` en `--java-options` (probado en Linux con jpackage 25; en Windows lo comprueba la CI mirando `arranque.log`). Si la JVM no pudo escribir ahí, deja el informe en la carpeta de trabajo o en la temporal; al abrir, SocialHub recoge de ahí los que nombran a SocialHub, avisa en `arranque.log` y conserva los 10 últimos y 60 días.
- **Falta de memoria:** **no** `-XX:+HeapDumpOnOutOfMemoryError`. Un volcado pesa lo que el montón (gigas, hasta un cuarto de la RAM), llenaría el disco y contiene datos personales y claves en claro. Se deja solo el aviso: el manejador global registra la falta de memoria con la memoria en uso y el máximo. **Registro del recolector (`-Xlog:gc`) no:** poca utilidad para este caso y la ruta de Windows con `C:` choca con el separador `:` de `-Xlog`.
- **Manejador global** (`Thread.setDefaultUncaughtExceptionHandler`, `UncaughtExceptionLogger`): hilo y traza a `arranque.log` y `socialhub.log`; nunca lanza. `main` también registra cualquier fallo antes de relanzarlo.
- **En uso:** errores de la app en `socialhub.log` (INFO). Filtro `ApiErrorLogFilter`: 5xx de la API con método, ruta y mensaje de la excepción, sin la consulta (`?…`) ni cuerpos; no registra el 503 de «SocialHub está parado». Las excepciones de tareas programadas ya las registra Spring como ERROR y la tarea sigue programada; una prueba lo vigila.
- **Rotación:** `socialhub.log` a 10 MB por archivo, 30 días y tope total de 300 MB (viejos en `.gz`, limpieza al arrancar). `pg_ctl.log` y `postgres.log` (que solo crecían) se apartan a `.1` al abrir si pasan de 20 MB, con PostgreSQL parado; su contenido y sus nombres no cambian.
- **Sin secretos:** `SecretRedactor` tapa `clave=valor`, JSON `"token":"…"`, `?key=`/`?code=`, `Bearer …` y formatos conocidos (Google, GitHub, OpenAI, Slack, Meta, JWT). La expresión vive en `application.yml` (`socialhub.logging.redaction-regex`, sin llaves ni comillas porque rompen un patrón de Logback) y Logback la aplica al mensaje y a la traza de `socialhub.log`; `arranque.log` y la salida estándar pasan por la misma expresión en Java. Pruebas: misma expresión en Java y en el yml; con Spring de verdad y el registro en archivo, ningún secreto de la lista aparece en `socialhub.log` ni en una traza.

**Pantalla (C):** en «Operaciones externas» (ya existía), la ruta de la carpeta de registros y el botón «Abrir carpeta de registros». API: `GET /api/app/logs` (ruta) y `POST /api/app/logs/open` (con la cabecera de protección; no recibe ninguna ruta y lanza el explorador sin intérprete de órdenes). Nada más: sin pantalla nueva.

**Límites conocidos:** si falta `socialhub.jar` o el lanzador no puede crear la JVM, el `.exe` de jpackage solo enseña su propio aviso y no hay Java en marcha que registre nada: ahí vale el registro de la instalación. El mensaje nativo de la JVM (avisos de HotSpot) sigue yendo al descriptor nativo, que Java no puede redirigir sin `-Xlog`.

## Fase 12 · Desplegables en lugar de texto libre (16 nov 2026)

Petición de Raúl: los campos que son una elección entre opciones cerradas deben ser desplegables. Auditados todos los formularios del frontal (decisión técnica salvo lo marcado como duda):
- **Convertido:** publicación (editor) · «Vía» pasa de texto a desplegable con `API oficial`, `Publer`, `Publora` (los nombres de `PublishRoute.Id` del backend, constante `PUBLISH_ROUTES` en `model.ts`). Si la publicación trae otro valor (p. ej. antiguo), se conserva como opción para no perderlo.
- **Ya eran desplegables:** plataforma, «Empieza como», motivo de rechazo, marca y estado de la promoción, tipo/modelo/dónde/origen de las filas de promoción, modelo y tipo de encargo de Claude, promoción en encargos, sistema en operaciones, cuenta de Instagram detectada, cuenta de Publora por red, agente de la estación.
- **Se dejan como texto (valor libre legítimo):** cuenta de Publer (nombre tal como sale en Publer), ID de cuenta de Instagram (alternativa a la detectada), cadencia, «Cuándo» de automatizaciones, rutas de carpetas, nombres, hitos, parámetros, «Vía» de las vías de la promoción (nombres libres como «Publer por Chrome»), situación y notas.
- **Duda para Raúl:** «Tipo» de publicación (Carrusel, Historia, Reel, Clip…): hoy es texto con sugerencias y el backend no lo valida; Claude puede proponer otros tipos. No se ha cambiado.
