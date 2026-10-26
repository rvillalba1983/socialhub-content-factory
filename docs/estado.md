# SocialHub · estado actual (para retomar)

Última actualización: 26 oct 2026, 19:40. Se retoma el **domingo 1 nov a las 10:00**, con un aviso programado en la sesión.

## Dónde estamos

- **Fase 0:** cerrada (`docs/fase-0.md`).
- **Fase 1:** cerrada. Propuesta visual «Moderna» elegida (`docs/prototipos/`).
- **Fase 2:** en curso (`docs/fase-2.md`).
  - **Bloque 1 · backend:** PR #1 (`fase-2/backend-base`). CI en verde y 24 pruebas. **Pendiente del OK de Raúl para fusionar.**
  - **Bloque 2 · frontend React:** rama `fase-2/frontend-react` (WIP, sale de la rama del bloque 1). Tiene el anillo, los paneles, el centro de control, el acceso, los avisos en directo y 3 pruebas. El backend sirve la web compilada.

## Siguiente paso (bloque 2)

1. **Arreglar el lienzo vacío:** sale con altura 0 porque `body` es un grid con una sola fila automática. En `frontend/src/styles.css`, antes de `#root{…}`, añadir `body{margin:0;display:block}`.
2. Recompilar la web (`npm run build` en `frontend/`) y el jar (`mvn -DskipTests package` en `backend/`).
3. Hacer la revisión visual con capturas:
   - Vista Fábrica.
   - Panel de estación.
   - Panel de tarea.
   - Centro de control y su calendario.
   - Modo oscuro.
   - Móvil.
4. Abrir la PR del bloque 2 y esperar el OK de Raúl.

## Cómo se trabaja

- **Dudas:** ante cualquier duda funcional o técnica, preguntar a Raúl antes de actuar, de una pregunta en una.
- **Guardar a menudo:** commit y push a la rama tras cada paso que funcione.
- **Ramas y fusiones:** una rama y una PR por bloque. Solo se fusiona con el OK de Raúl.
- **Entorno de Claude en la nube:** acceso de red «Custom», con la lista por defecto más `api.adoptium.net`.
  - Java 25 se instala en `/opt/jdk`.
  - Maven y npm necesitan el proxy de la sesión. Hay envoltorios en `/opt/jdk/mvnp`, `/opt/jdk/mvnu` y `/opt/jdk/npmp`, que se pierden si el contenedor se reinicia.
  - Las pruebas que arrancan PostgreSQL no pueden ejecutarse como `root`.
