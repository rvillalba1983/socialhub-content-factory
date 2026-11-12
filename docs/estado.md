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
- SocialHub pasa a la versión 1.0.0 (decisión de Raúl, 12 nov): el próximo instalador, `.msi` 1.0.0, se instala encima de la 0.9.1.
- Ha dado de alta la marca Litoques y la promoción «Test inicial» (empieza el 9 nov).
- Pendiente de Raúl:
  - Carpeta base de Litoques en «Carpetas de Drive» (botón «Crear las que faltan»).
  - Carpeta de copias en «Copias».
  - Clave de Publora y cuenta de cada marca.
  - Comprobar que las tareas programadas recogen los encargos de verdad:
    - «Encargos de SocialHub», en la nube: 9:40, 14:40 y 20:40.
    - «Publicar con Chrome (SocialHub)», en su PC: cada hora de 8:40 a 22:40, con Chrome abierto y la sesión de Publer.

## Fase 11 (cerrada el 11 nov, pendiente de merge)

- PR #35 (`fase-11-arreglos`). Se une junto con la #33 (scripts de Windows «Siempre abierto» y «Ficha del equipo») y la #34 (arreglo del mapa del código). Tras el merge: instalador `.msi` 0.9.3.
- Qué incluye: los puntos 0 a 8 de `docs/fase-11.md`, más los arreglos de la revisión y el aviso de «Promoción activada sola».
- Comprobado con Playwright en modo real: los 10 puntos de «Cómo sabremos que está bien», sin errores en la consola.
- Fuera del código, 11 nov:
  - Claude reactivó la tarea «Publicar con Chrome» con el OK de Raúl.
  - El script «Siempre abierto» está instalado en el portátil de Raúl.
  - TikTok: se usa la cuenta personal de Raúl, renombrada a `@litoques.eldelito`, pública, con nombre «Lito · Litoques · El DeLito».

## Siguiente: fase 12 (una sola fase, decisión de Raúl del 11 nov)

Todo lo pendiente va en una fase, por bloques y con una PR por bloque:
1. **Métricas:** pestaña de la promoción con métricas reales. Dudas para Raúl, de una en una:
   - qué cifras: hoy se leen 6 por publicación y la pestaña enseña 8; impresiones, seguidores ganados y clics son de la cuenta;
   - cómo resumirlas: totales, medias o comparación por formato, día, hora y red;
   - muestra mínima para sacar conclusiones;
   - medir también a las 24–48 h o solo a los 7 días;
   - Instagram y TikTok juntos o por separado.
2. **macOS:** instalador (`.dmg`) y pruebas.
3. **API oficial de Instagram**, y estudio de si programar con Chrome desde la web de Instagram o desde Meta Business Suite (gratis) puede sustituir a Publer.
4. **TikTok en SocialHub:**
   - Raúl pasa `@litoques.eldelito` a cuenta de empresa desde el móvil y pone el enlace en la descripción;
   - conectarla en Publer;
   - añadirla a las dos marcas.
5. **Detalles visuales de «Necesita atención»** en el mapa: los textos se cortan con «…» y, con 3 avisos o más, la tarjeta tapa la estación 07. Con muchas recomendaciones, el «Ver N más» de la franja queda suelto.

También pendiente, fuera de fase: comparar el portátil con la torre HP de 2013 (`ficha-equipo.bat`) cuando la torre tenga cables. Buffer: descartado para siempre.
