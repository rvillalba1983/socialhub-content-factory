# SocialHub — Content Factory

Fábrica de contenido para redes sociales: investiga, produce, programa, publica y analiza contenido para varias marcas desde una única interfaz visual. Empieza con las promociones de **El DeLito** y **Litoques**.

## Estado

| Fase | Estado |
|---|---|
| 0 · Análisis y decisiones técnicas | ✅ Aprobada (`docs/fase-0.md`) |
| 1 · Prototipo visual | ✅ Validado: propuesta «Moderna» elegida (`docs/prototipos/`) |
| 2 · Backend y persistencia | Pendiente |

## Documentación

- [`docs/especificacion.md`](docs/especificacion.md): especificación maestra del producto, la fuente de verdad.
- [`docs/fase-0.md`](docs/fase-0.md): especificación técnica inicial (versiones, arquitectura, contratos y riesgos).
- [`docs/decisiones.md`](docs/decisiones.md): registro de decisiones.
- [`docs/prototipos/`](docs/prototipos/): prototipos navegables. Se abren directamente en el navegador.
  - `propuesta-moderna.html` es la elegida.
  - `propuesta-warcraft.html` es la alternativa.
- [`contracts/`](contracts/): esquemas JSON de intercambio entre SocialHub y los agentes de IA.

## Estructura

```
backend/     Spring Boot 4 · Java 25 · PostgreSQL incluido · Flyway (Fase 2)
frontend/    React · TypeScript · Vite (Fase 2)
contracts/   Esquemas JSON versionados
docs/        Especificación, decisiones y prototipos
```

## Reglas del proyecto

- Ante cualquier duda, funcional o técnica, no se hace nada sin preguntar antes a Raúl.
- Solo herramientas gratuitas; nada de pago sin autorización expresa.
- Nunca se envían correos ni mensajes directos en nombre de Raúl, y nunca se publica al instante.
- Los secretos nunca se suben al repositorio.
- El código va en inglés; la documentación y la interfaz, en español.
