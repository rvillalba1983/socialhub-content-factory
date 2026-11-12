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
