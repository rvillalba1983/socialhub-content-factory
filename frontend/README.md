# frontend

React 19 + TypeScript 7 + Vite. Parte de la propuesta visual «Moderna» (`docs/prototipos/propuesta-moderna.html`). El anillo de producción se dibuja en SVG propio, sin React Flow.

El backend sirve la web compilada en http://localhost:8686. No hay pantalla de acceso, porque SocialHub solo se abre en este ordenador.

## Pantallas

- **Fábrica:** el anillo con las 8 estaciones, las promociones, los avisos y la armería. Tiene zoom semántico en tres niveles: Fábrica, Estación y Tarea.
- **Paneles:** de estación, de tarea y de recurso de la armería. Las órdenes que se ofrecen son las que devuelve el backend en `available_commands`.
- **Centro de control de cada promoción:** resumen, calendario, publicaciones, tareas, recursos, automatización, métricas y configuración.
- **Operaciones externas:** botón «Operaciones» de la cabecera. Muestra las llamadas a PostgreSQL, Drive, Publer, etc., con su resultado, duración, error e identificador de traza.

## API first

Los tipos de la API se **generan** desde `contracts/openapi.yaml` con openapi-typescript:

```bash
cd tools/openapi
npm install
npm run generate     # escribe src/api/schema.d.ts
```

- `src/api/schema.d.ts` es código generado: no se edita a mano.
- `src/api/types.ts` da nombres cómodos a los tipos generados.
- `src/model.ts` añade las etiquetas, los colores y las fechas para la interfaz.
- El generador va en `tools/openapi` con su propio TypeScript 5, porque openapi-typescript todavía no funciona con TypeScript 7. Solo se usa para generar; no forma parte de la web.
- GitHub Actions comprueba que `schema.d.ts` coincide con el contrato.

## Desarrollo

```bash
npm install
npm run dev      # http://localhost:5173; la API se reenvía al backend en localhost:8686
npm test         # Vitest
npm run build    # comprueba tipos y compila en dist/
```

Para que el backend sirva la web nueva: `npm run build` y luego, en `backend/`, `mvn clean package`. Sin `clean` pueden quedar ficheros de una compilación anterior.
