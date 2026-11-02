# SocialHub · estado actual (para retomar)

Última actualización: 2 nov 2026, 23:30.

## Dónde estamos

- **Fases 0 a 5:** terminadas y en `main`. Detalle en `docs/fase-3.md` y `docs/fase-4-5.md`.
  - Tarea programada «Encargos de SocialHub» creada (9:40, 14:40 y 20:40), en la nube.
- **Fase 6:** plan en `docs/fase-6.md`. El bloque 1 (identidad de marca y control de calidad) está en la rama `fase-6/identidad-calidad`.
- **Pendiente en el PC de Raúl:**
  - Arrancar SocialHub en Windows.
  - Dar de alta sus marcas y promociones con sus carpetas reales de G:, incluida la de «Encargos».
  - Probar un encargo de verdad con la tarea programada.
- **Siguiente:** fase 6, bloque 2 (memoria editorial).

## Cómo se trabaja

- **Dudas:** ante cualquier duda funcional o técnica, preguntar a Raúl antes de actuar, de una pregunta en una.
- **Librerías:** preguntar a Raúl antes de añadir cualquier librería nueva.
- **Guardar a menudo:** commit y push a la rama tras cada paso que funcione.
- **Ramas y fusiones:** una rama y una PR por bloque. Solo se fusiona con el OK de Raúl.
- **Pruebas antes de entregar una PR:** Linux y Windows en GitHub Actions (`otros_sistemas=windows`), sin preguntar. macOS solo si Raúl lo pide.
- **Compilación:** en el entorno de Claude en la nube (Linux, Java 25, Maven, Node 22) y en GitHub Actions.
  - Envoltorios: `/opt/jdk/mvnu` (Maven como usuario `claude`) y `/opt/jdk/npmp`. Se pierden si el contenedor se reinicia.
  - Las pruebas que arrancan PostgreSQL no pueden ejecutarse como `root`.
