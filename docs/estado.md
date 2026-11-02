# SocialHub · estado actual (para retomar)

Última actualización: 2 nov 2026.

## Dónde estamos

- **Fase 0:** cerrada (`docs/fase-0.md`).
- **Fase 1:** cerrada. Propuesta visual «Moderna» elegida (`docs/prototipos/`).
- **Fase 2:** en curso (`docs/fase-2.md`).
  - **Bloque 1 · backend:** fusionado en `main` (PR #1), rehecho según la revisión de Raúl del 1 de noviembre.
  - **Bloque 2 · frontal React:** rama `fase-2/frontend`, adaptada a la nueva API:
    - Tipos generados desde OpenAPI.
    - Órdenes por `/commands`.
    - Sin pantalla de acceso.
    - Pantalla «Operaciones externas».
    - Revisión visual hecha con capturas.
    - PR abierta. **Esperando la revisión de Raúl.**

## Cómo se trabaja

- **Dudas:** ante cualquier duda funcional o técnica, preguntar a Raúl antes de actuar, de una pregunta en una.
- **Librerías:** preguntar a Raúl antes de añadir cualquier librería nueva.
- **Guardar a menudo:** commit y push a la rama tras cada paso que funcione.
- **Ramas y fusiones:** una rama y una PR por bloque. Solo se fusiona con el OK de Raúl.
- **Compilación:** en el entorno de Claude en la nube (Linux, Java 25, Maven, Node 22) y en GitHub Actions.
  - Envoltorios: `/opt/jdk/mvnu` (Maven como usuario `claude`) y `/opt/jdk/npmp`. Se pierden si el contenedor se reinicia.
  - Las pruebas que arrancan PostgreSQL no pueden ejecutarse como `root`.
