# SocialHub · estado actual (para retomar)

Última actualización: 2 nov 2026, 18:15.

## Dónde estamos

- **Fases 0, 1, 2 y 3:** terminadas y en `main`.
  - Fase 3: modos real y demo, alta de marcas y promociones, motor de tareas, herramientas locales (FFmpeg incluido y Java 2D) y piezas de promo.py en Java.
  - Probado en Linux y en Windows con GitHub Actions.
- **Pendiente en el PC de Raúl:**
  - Arrancar SocialHub en Windows.
  - Dar de alta sus promociones con sus carpetas reales de G:.
  - Encargar unas piezas de prueba.
- **Siguiente:** elegir la siguiente fase de `docs/especificacion.md`: Fase 4 (Google Drive) o Fase 5 (orquestador de IA). Antes de empezar, preguntas a Raúl de una en una.

## Cómo se trabaja

- **Dudas:** ante cualquier duda funcional o técnica, preguntar a Raúl antes de actuar, de una pregunta en una.
- **Librerías:** preguntar a Raúl antes de añadir cualquier librería nueva.
- **Guardar a menudo:** commit y push a la rama tras cada paso que funcione.
- **Ramas y fusiones:** una rama y una PR por bloque. Solo se fusiona con el OK de Raúl.
- **Compilación:** en el entorno de Claude en la nube (Linux, Java 25, Maven, Node 22) y en GitHub Actions.
  - Envoltorios: `/opt/jdk/mvnu` (Maven como usuario `claude`) y `/opt/jdk/npmp`. Se pierden si el contenedor se reinicia.
  - Las pruebas que arrancan PostgreSQL no pueden ejecutarse como `root`.
