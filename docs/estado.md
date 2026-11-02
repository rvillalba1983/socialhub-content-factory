# SocialHub · estado actual (para retomar)

Última actualización: 2 nov 2026, 10:00.

## Dónde estamos

- **Fases 0, 1 y 2:** terminadas. El backend y el frontal están en `main`.
  - Arranque comprobado en Windows y macOS con GitHub Actions.
  - Falta probarlo en el PC de Raúl.
- **Siguiente:** Fase 3, motor de tareas y herramientas locales (`docs/especificacion.md`). Antes de empezar hay que plantear las preguntas a Raúl, de una en una.

## Cómo se trabaja

- **Dudas:** ante cualquier duda funcional o técnica, preguntar a Raúl antes de actuar, de una pregunta en una.
- **Librerías:** preguntar a Raúl antes de añadir cualquier librería nueva.
- **Guardar a menudo:** commit y push a la rama tras cada paso que funcione.
- **Ramas y fusiones:** una rama y una PR por bloque. Solo se fusiona con el OK de Raúl.
- **Compilación:** en el entorno de Claude en la nube (Linux, Java 25, Maven, Node 22) y en GitHub Actions.
  - Envoltorios: `/opt/jdk/mvnu` (Maven como usuario `claude`) y `/opt/jdk/npmp`. Se pierden si el contenedor se reinicia.
  - Las pruebas que arrancan PostgreSQL no pueden ejecutarse como `root`.
