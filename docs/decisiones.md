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

