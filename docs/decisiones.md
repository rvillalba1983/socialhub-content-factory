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
- Compilación: Raúl prefiere desbloquear Maven Central y la descarga de Java 25 en el entorno de Claude. Pendiente de que lo haga desde el ordenador.
