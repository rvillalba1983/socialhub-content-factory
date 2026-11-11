# SocialHub · estado actual (para retomar)

Última actualización: 11 nov 2026. Cómo se trabaja: `docs/como-trabajar.md` (una fase por conversación).

## Dónde estamos

Las fases 0 a 10 de la especificación están hechas y en `main`, salvo lo que se indica:

| Fase | Documento | Estado |
|---|---|---|
| 0 · Análisis y decisiones | `fase-0.md` | ✅ |
| 1 · Prototipo visual | `prototipos/` | ✅ Propuesta «Moderna» |
| 2 · Backend, persistencia y frontal | `fase-2.md` | ✅ |
| 3 · Motor de tareas y herramientas locales | `fase-3.md` | ✅ |
| 4 y 5 · Drive y encargos a Claude | `fase-4-5.md` | ✅ |
| 6 · Producción editorial | `fase-6.md` | ✅ |
| 7 · Publicación | `fase-7.md` | ✅ Publer, Publora y calendario. API oficial (bloque 3) aparcada por Raúl |
| 8 · Métricas y autonomía | `fase-8.md` | ✅ |
| 9 · Copias e instaladores | `fase-9.md` | ✅ Copias e instalador de Windows (`.msi` 0.9.1). Falta el `.dmg` |
| 10 · Primera semana real: guía y propuestas iniciales | `fase-10.md` | ✅ |

Desde la fase 9 también hay en `main` (PR #22–#30): manual funcional con diagramas D2, mapa 3D del código en cada PR, arreglos del arranque en Windows y la puesta en marcha de marcas nuevas (carpeta base, avisos de encargos retenidos y propuestas de la primera semana).

Desde la fase 10 (PR #32) la pantalla principal tiene la franja «Qué hacer ahora» encima del mapa, con recomendaciones que desaparecen al hacerlas.

## En el PC de Raúl

- SocialHub 0.9.1 instalado y funcionando (`http://localhost:8686`). Datos y registros en `%LOCALAPPDATA%\SocialHub`.
- Instalador 0.9.2 pendiente de generar e instalar (Actions › Instaladores).
- Ha dado de alta la marca Litoques y la promoción «Test inicial» (empieza el 9 nov).
- Pendiente de Raúl:
  - Carpeta base de Litoques en «Carpetas de Drive» (botón «Crear las que faltan»).
  - Carpeta de copias en «Copias».
  - Clave de Publora y cuenta de cada marca.
  - Comprobar que las tareas programadas recogen los encargos de verdad:
    - «Encargos de SocialHub», en la nube: 9:40, 14:40 y 20:40.
    - «Publicar con Chrome (SocialHub)», en su PC: cada hora de 8:40 a 22:40, con Chrome abierto y la sesión de Publer.

## Fase 11 (en curso, 11 nov)

- Plan en `docs/fase-11.md`, rama `fase-11-arreglos`. Incluye las pruebas en la nube y la semana real de Raúl, sacada de los logs de su PC y de los encargos de Drive. **Falta su OK**, incluido el margen de 6 h para las propuestas «pasadas».
- Causa de que no le llegaran las propuestas: un encargo sin fecha recalcula su semana al leerse (fallo 0 del plan).
- PR #33 (`pc-siempre-abierto`, en verde, sin merge):
  - «Siempre abierto»: Claude, OpenCode, Chrome y SocialHub. Ya está instalado en el portátil de Raúl y funciona.
  - Ficha del equipo.
- Pendiente para otro día: comparar el portátil con la torre HP de 2013, que aún no tiene cables, con `ficha-equipo.bat` en las dos.

## Siguiente

Lo decide Raúl al empezar la próxima conversación. Candidatos:
- Comprobar en real la franja «Qué hacer ahora» con Litoques y seguir con lo que salga de la primera semana (fallos y mejoras de uso).
- Pestaña «Métricas» de la promoción con las métricas reales (ofrecido, sin respuesta).
- Instalador de macOS (`.dmg`) y pruebas en macOS, cuando haya una versión utilizable.
- API oficial de publicación (fase 7, bloque 3), si Raúl la retoma.
