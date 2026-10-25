# Instrucciones para Claude en este repositorio

- Háblale a Raúl de tú y en español.
- **Regla principal:** si tienes cualquier duda, funcional o técnica, no hagas nada. Pregúntale primero a Raúl, de una pregunta en una.
- Lee `docs/especificacion.md`, `docs/fase-0.md` y `docs/decisiones.md` antes de cambiar nada. Registra cada decisión nueva en `docs/decisiones.md`.
- El código va en inglés (clases, tablas, endpoints); la documentación y los textos de la interfaz, en español.
- Pila técnica:
  - Java 25 LTS, Spring Boot 4.1.x, Maven, Flyway, PostgreSQL incluido y arrancado por la app.
  - React + TypeScript + Vite, con el anillo en SVG propio, sin React Flow.
  - Monolito modular con arquitectura hexagonal.
- Uso de la IA:
  - SocialHub no llama a la API de Claude. Claude trabaja desde sus tareas programadas.
  - Claude intercambia encargos y resultados por una carpeta de Google Drive, con los contratos de `contracts/`.
- Solo herramientas gratuitas; nada de pago sin autorización expresa. No se suben secretos.
- No marques nada como hecho sin probarlo. No simules publicaciones.
