# SocialHub · estado actual (para retomar)

Última actualización: 3 nov 2026, 03:30.

## Dónde estamos

- **Fases 0 a 5:** terminadas y en `main`. Detalle en `docs/fase-3.md` y `docs/fase-4-5.md`.
  - Tarea programada «Encargos de SocialHub» creada (9:40, 14:40 y 20:40), en la nube.
- **Fase 6:** terminada y en `main` (`docs/fase-6.md`).
- **Fases 7, 8 y 9:** preguntas respondidas (`docs/fases-7-9.md`).
- **Fase 7, bloque 1** (motor de publicación y vía Publer): en `main`.
- **Fase 7, bloque 2** (Publora): hecho en la rama `fase-7/publora` (`docs/fase-7.md`).
- **Pendiente en el PC de Raúl:**
  - Arrancar SocialHub en Windows.
  - Dar de alta sus marcas y promociones con sus carpetas reales de G:, incluida la de «Encargos».
  - Probar un encargo de verdad con la tarea programada.
- **Siguiente:** fase 7, bloque 3 (API oficial).
  - Hace falta que Raúl pegue la clave de API de Publora en la app (Marcas › editar › Publora) y elija la cuenta de cada marca.
  - Tarea programada «Publicar con Chrome (SocialHub)» creada en su PC (cada hora de 8:40 a 22:40). Necesita Chrome abierto con la sesión de Publer.

## Cómo se trabaja

- **Dudas:** ante cualquier duda funcional o técnica, preguntar a Raúl antes de actuar, de una pregunta en una.
- **Librerías:** preguntar a Raúl antes de añadir cualquier librería nueva.
- **Guardar a menudo:** commit y push a la rama tras cada paso que funcione.
- **Ramas y fusiones:** una rama y una PR por bloque. Solo se fusiona con el OK de Raúl.
- **Pruebas antes de entregar una PR:** Windows y macOS en GitHub Actions (`otros_sistemas=windows-y-macos`; Linux va siempre incluido), sin preguntar.
- **Compilación:** en el entorno de Claude en la nube (Linux, Java 25, Maven, Node 22) y en GitHub Actions.
  - Envoltorios: `/opt/jdk/mvnu` (Maven como usuario `claude`) y `/opt/jdk/npmp`. Se pierden si el contenedor se reinicia.
  - Las pruebas que arrancan PostgreSQL no pueden ejecutarse como `root`.
